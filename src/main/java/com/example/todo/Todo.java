package com.example.todo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "todo")
public class Todo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Todo() {
	}

	public Todo(String title) {
		this.title = title;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getTitle() { return title; }
	public boolean isCompleted() { return completed; }
	public Instant getCreatedAt() { return createdAt; }
	public void setTitle(String title) { this.title = title; }
	public void setCompleted(boolean completed) { this.completed = completed; }
}
