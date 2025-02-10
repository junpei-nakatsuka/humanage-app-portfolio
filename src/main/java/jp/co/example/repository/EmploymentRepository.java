package jp.co.example.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.entity.Employment;

public interface EmploymentRepository extends JpaRepository<Employment, Integer> {
	
    List<Employment> findByUserId(Integer userId);
    
    void deleteByUserId(Integer userId);
    
    Optional<Employment> findById(Integer id);
    
    List<Employment> findByStatusAndResignationDate(String status, LocalDate resignationDate);
        
    List<Employment> findByUserIdAndStatus(Integer userId, String status);
    
    List<Employment> findByStatus(String status);
    
    @Transactional
    default List<Employment> findByStatusAndResignationDateBefore(String status, LocalDate resignationDate) {
        return findByStatus(status).stream()
                .filter(employment -> employment.getResignationDate() != null && employment.getResignationDate().isBefore(resignationDate))
                .collect(Collectors.toList());
    }
}