package jp.co.example.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.entity.Evaluation;

public interface EvaluationRepository extends JpaRepository<Evaluation, Integer>{

}
