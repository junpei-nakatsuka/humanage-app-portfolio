package jp.co.example.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.entity.User;


public interface UserRepository extends JpaRepository<User, Integer>{
	
	Optional<User> findByEmail(String email);
    
    Optional<User> findByUsername(String username);
    
    List<User> findByStatus(String status);
    
    List<User> findByPhone(String phone);
	
    List<User> findByHireDateIsNotNull();
    
    List<User> findByRetirementDateIsNull();
    
    List<User> findByRetirementDateIsNotNull();
    
    List<User> findByDepartmentId(Integer departmentId);
    
    List<User> findByUsernameContainingOrEmailContainingOrPhoneContaining(String username, String email, String phone);
    
    List<User> findAllByOrderByUsernameAsc();
    
    List<User> findByUsernameContainingIgnoreCase(String username);
    
    Optional<User> findByResetToken(String resetToken);
    
    List<User> findByPostalCode(String postalCode);
    
    boolean existsByEmail(String email);
}
