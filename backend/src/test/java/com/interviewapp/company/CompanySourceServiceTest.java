package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
import com.interviewapp.company.UrlContentFetcher.ExtractedContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanySourceServiceTest {

    @Mock
    private CompanySourceRepository sourceRepository;

    @Mock
    private UrlContentFetcher urlContentFetcher;

    @InjectMocks
    private CompanySourceService service;

    private final UUID companyId = UUID.randomUUID();

    @Test
    void previewFetchは保存せずfetch結果を返す() {
        when(urlContentFetcher.fetch("https://example.com"))
                .thenReturn(new ExtractedContent("採用ページ", "本文テキスト"));

        FetchedSourcePreview preview = service.previewFetch("https://example.com");

        assertThat(preview.url()).isEqualTo("https://example.com");
        assertThat(preview.title()).isEqualTo("採用ページ");
        assertThat(preview.content()).isEqualTo("本文テキスト");
        verify(sourceRepository, times(0)).save(any());
    }

    @Test
    void addSourceはfetchして永続化する() {
        when(urlContentFetcher.fetch("https://example.com"))
                .thenReturn(new ExtractedContent("採用ページ", "本文テキスト"));
        when(sourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SourceResponse response = service.addSource(companyId, "https://example.com");

        assertThat(response.url()).isEqualTo("https://example.com");
        assertThat(response.title()).isEqualTo("採用ページ");
        ArgumentCaptor<CompanySource> captor = ArgumentCaptor.forClass(CompanySource.class);
        verify(sourceRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(companyId);
    }

    @Test
    void fetch失敗はそのまま例外が伝播する() {
        when(urlContentFetcher.fetch(any()))
                .thenThrow(new IllegalStateException("ソースの取得に失敗しました。URLを確認してください。"));

        assertThatThrownBy(() -> service.addSource(companyId, "https://broken.example"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("取得に失敗");
    }

    @Test
    void persistFetchedは再fetchせずそのまま保存する() {
        when(sourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        FetchedSourcePreview preview = new FetchedSourcePreview("https://example.com", "採用ページ", "本文テキスト");

        SourceResponse response = service.persistFetched(companyId, preview);

        assertThat(response.content()).isEqualTo("本文テキスト");
        verify(urlContentFetcher, times(0)).fetch(any());
    }

    @Test
    void listSourcesは作成日時の新しい順で返す() {
        CompanySource s = new CompanySource(companyId, "https://example.com", "title", "content");
        when(sourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(s));

        assertThat(service.listSources(companyId)).hasSize(1);
    }

    @Test
    void deleteSourceは存在しないと404相当の例外() {
        UUID missing = UUID.randomUUID();
        when(sourceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSource(missing)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findRelevantはクエリと重なりが大きいソースを上位にする() {
        CompanySource matching = new CompanySource(companyId, "https://a.example", "採用情報", "新卒採用に力を入れている");
        CompanySource unrelated = new CompanySource(companyId, "https://b.example", "アクセス", "本社所在地は東京です");
        when(sourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(unrelated, matching));

        List<CompanySource> result = service.findRelevant(companyId, "新卒採用について教えてください", 1);

        assertThat(result).containsExactly(matching);
    }

    @Test
    void findRelevantはソースが無ければ空リスト() {
        when(sourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());

        assertThat(service.findRelevant(companyId, "何か質問", 3)).isEmpty();
    }

    @Test
    void scoreは重なる文字バイグラムの数() {
        assertThat(CompanySourceService.score("新卒採用", "新卒採用に力を入れている")).isGreaterThan(0);
        assertThat(CompanySourceService.score(null, "本文")).isEqualTo(0);
        assertThat(CompanySourceService.score("クエリ", null)).isEqualTo(0);
    }
}
