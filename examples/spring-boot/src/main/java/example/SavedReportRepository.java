package example;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
}
