package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TodoRepositoryTests {

	@Autowired
	TodoRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void filtersByCompletedInCreationOrder() {
		Todo a = repository.save(new Todo("a"));
		Todo b = repository.save(new Todo("b"));
		Todo c = repository.save(new Todo("c"));
		b.setCompleted(true);
		repository.flush();

		assertThat(repository.findAllByOrderByCreatedAtAscIdAsc()).extracting(Todo::getTitle).containsExactly("a", "b", "c");
		assertThat(repository.findByCompletedOrderByCreatedAtAscIdAsc(false)).extracting(Todo::getTitle).containsExactly("a", "c");
		assertThat(repository.findByCompletedOrderByCreatedAtAscIdAsc(true)).extracting(Todo::getTitle).containsExactly("b");
		assertThat(repository.countByCompleted(false)).isEqualTo(2);
	}

	@Test
	void deleteCompletedRemovesOnlyCompleted() {
		repository.save(new Todo("keep"));
		repository.save(new Todo("drop")).setCompleted(true);
		repository.flush();

		assertThat(repository.deleteCompleted()).isEqualTo(1);
		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("keep");
	}

	@Test
	void databaseRejectsBlankTitle() {
		assertThatThrownBy(() -> jdbc.sql("insert into todo (title) values ('   ')").update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
