package com.example.todo;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TodoService {

	private final TodoRepository repository;

	public TodoService(TodoRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<Todo> list(TodoFilter filter) {
		return switch (filter) {
			case ALL -> repository.findAllByOrderByCreatedAtAscIdAsc();
			case ACTIVE -> repository.findByCompletedOrderByCreatedAtAscIdAsc(false);
			case COMPLETED -> repository.findByCompletedOrderByCreatedAtAscIdAsc(true);
		};
	}

	@Transactional(readOnly = true)
	public long activeCount() {
		return repository.countByCompleted(false);
	}

	@Transactional(readOnly = true)
	public Todo get(long id) {
		return repository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
	}

	public Todo create(String title) {
		return repository.save(new Todo(title.strip()));
	}

	public void rename(long id, String title) {
		get(id).setTitle(title.strip());
	}

	public void setCompleted(long id, boolean completed) {
		get(id).setCompleted(completed);
	}

	public void delete(long id) {
		repository.delete(get(id));
	}

	public int clearCompleted() {
		return repository.deleteCompleted();
	}
}
