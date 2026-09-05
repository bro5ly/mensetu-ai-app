package com.interviewapp.company;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final CompanySourceService companySourceService;

    public SourceController(CompanySourceService companySourceService) {
        this.companySourceService = companySourceService;
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sourceId) {
        companySourceService.deleteSource(sourceId);
        return ResponseEntity.noContent().build();
    }
}
