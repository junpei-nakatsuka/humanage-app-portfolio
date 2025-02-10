package jp.co.example.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Integer>{
	
	Optional<Department> findByDepartmentName(String departmentName);
}
