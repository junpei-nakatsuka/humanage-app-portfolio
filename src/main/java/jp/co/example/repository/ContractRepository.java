package jp.co.example.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jp.co.example.entity.Contract;

public interface ContractRepository extends JpaRepository<Contract, Integer>{

	List<Contract> findByUserId(Integer userId);
	
	Contract findTopByUserIdOrderByContractDateDesc(Integer userId);
	
	@Query("SELECT c FROM Contract c WHERE c.user.id = :userId AND (c.expiryDate IS NULL OR c.expiryDate >= :currentDate)")
	List<Contract> findActiveContractsByUserId(Integer userId, LocalDate currentDate);
}
