package com.example.todo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	List<Todo> findAllByOrderByCreatedAtAscIdAsc();

	List<Todo> findByCompletedOrderByCreatedAtAscIdAsc(boolean completed);

	long countByCompleted(boolean completed);

	@Modifying
	@Query("delete from Todo t where t.completed = true")
	int deleteCompleted();
}
