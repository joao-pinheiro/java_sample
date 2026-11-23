package com.example.todo;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TodoController {

	private final TodoService service;

	public TodoController(TodoService service) {
		this.service = service;
	}

	@GetMapping("/")
	String root() {
		return "redirect:/todos";
	}

	@GetMapping("/todos")
	String list(@RequestParam(defaultValue = "ALL") TodoFilter filter, Model model) {
		populateList(filter, model);
		model.addAttribute("form", new TodoForm(""));
		return "todos/list";
	}

	@PostMapping("/todos")
	String create(@RequestParam(defaultValue = "ALL") TodoFilter filter,
			@Valid @ModelAttribute("form") TodoForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			populateList(filter, model);
			return "todos/list";
		}
		service.create(form.title());
		return redirectTo(filter);
	}

	@PostMapping("/todos/{id}/completed")
	String setCompleted(@PathVariable long id, @RequestParam boolean completed,
			@RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.setCompleted(id, completed);
		return redirectTo(filter);
	}

	@PostMapping("/todos/{id}/delete")
	String delete(@PathVariable long id, @RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.delete(id);
		return redirectTo(filter);
	}

	@PostMapping("/todos/clear-completed")
	String clearCompleted(@RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.clearCompleted();
		return redirectTo(filter);
	}

	@GetMapping("/todos/{id}/edit")
	String edit(@PathVariable long id, Model model) {
		Todo todo = service.get(id);
		model.addAttribute("todo", todo);
		model.addAttribute("form", new TodoForm(todo.getTitle()));
		return "todos/edit";
	}

	@PostMapping("/todos/{id}")
	String rename(@PathVariable long id, @Valid @ModelAttribute("form") TodoForm form,
			BindingResult result, Model model) {
		if (result.hasErrors()) {
			model.addAttribute("todo", service.get(id));
			return "todos/edit";
		}
		service.rename(id, form.title());
		return "redirect:/todos";
	}

	private void populateList(TodoFilter filter, Model model) {
		model.addAttribute("todos", service.list(filter));
		model.addAttribute("activeCount", service.activeCount());
		model.addAttribute("filter", filter);
	}

	private static String redirectTo(TodoFilter filter) {
		return "redirect:/todos?filter=" + filter.name();
	}
}
