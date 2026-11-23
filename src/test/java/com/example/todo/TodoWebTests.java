package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TodoWebTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	TodoRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void rootRedirectsToList() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/todos"));
	}

	@Test
	void createThenListShowsTodo() throws Exception {
		mvc.perform(post("/todos").param("title", "  Buy milk  ").param("filter", "ACTIVE").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/todos?filter=ACTIVE"));

		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("Buy milk");
		mvc.perform(get("/todos"))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/list"))
			.andExpect(content().string(containsString("Buy milk")))
			.andExpect(content().string(containsString("1 item(s) left")));
	}

	@Test
	void blankTitleIsRejectedAndNothingSaved() throws Exception {
		mvc.perform(post("/todos").param("title", "   ").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/list"))
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void titleLongerThan200IsRejected() throws Exception {
		mvc.perform(post("/todos").param("title", "x".repeat(201)).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void completedSetsTheRequestedStateIdempotently() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/todos/{id}/completed", todo.getId()).param("completed", "true").param("filter", "ACTIVE").with(csrf()))
				.andExpect(redirectedUrl("/todos?filter=ACTIVE"));
			assertThat(repository.findById(todo.getId()).orElseThrow().isCompleted()).isTrue();
		}
		mvc.perform(post("/todos/{id}/completed", todo.getId()).param("completed", "false").with(csrf()));
		assertThat(repository.findById(todo.getId()).orElseThrow().isCompleted()).isFalse();
	}

	@Test
	void completedFormOffersTheOppositeState() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		mvc.perform(get("/todos"))
			.andExpect(content().string(containsString("name=\"completed\" value=\"true\"")))
			.andExpect(content().string(not(containsString("name=\"completed\" value=\"false\""))));
		todo.setCompleted(true);
		repository.save(todo);
		mvc.perform(get("/todos"))
			.andExpect(content().string(containsString("name=\"completed\" value=\"false\"")))
			.andExpect(content().string(not(containsString("name=\"completed\" value=\"true\""))));
	}

	@Test
	void listFormsPreserveTheCurrentFilter() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		long id = todo.getId();
		mvc.perform(get("/todos").param("filter", "ACTIVE"))
			.andExpect(content().string(containsString("action=\"/todos?filter=ACTIVE\"")))
			.andExpect(content().string(containsString("action=\"/todos/" + id + "/completed?filter=ACTIVE\"")))
			.andExpect(content().string(containsString("action=\"/todos/" + id + "/delete?filter=ACTIVE\"")))
			.andExpect(content().string(containsString("action=\"/todos/clear-completed?filter=ACTIVE\"")));
	}

	@Test
	void filterShowsOnlyMatchingTodos() throws Exception {
		repository.save(new Todo("open-item"));
		Todo done = repository.save(new Todo("done-item"));
		mvc.perform(post("/todos/{id}/completed", done.getId()).param("completed", "true").with(csrf()));

		mvc.perform(get("/todos").param("filter", "ACTIVE"))
			.andExpect(content().string(containsString("1 item(s) left")))
			.andExpect(content().string(containsString("open-item")))
			.andExpect(content().string(not(containsString("done-item"))));
		mvc.perform(get("/todos").param("filter", "COMPLETED"))
			.andExpect(content().string(containsString("done-item")))
			.andExpect(content().string(not(containsString("open-item"))));
	}

	@Test
	void deleteRemovesTodo() throws Exception {
		Todo keep = repository.save(new Todo("keep"));
		Todo todo = repository.save(new Todo("drop"));
		mvc.perform(post("/todos/{id}/delete", todo.getId()).param("filter", "COMPLETED").with(csrf()))
			.andExpect(redirectedUrl("/todos?filter=COMPLETED"));
		assertThat(repository.findAll()).extracting(Todo::getId).containsExactly(keep.getId());
	}

	@Test
	void clearCompletedKeepsActive() throws Exception {
		repository.save(new Todo("keep"));
		Todo done = repository.save(new Todo("drop"));
		mvc.perform(post("/todos/{id}/completed", done.getId()).param("completed", "true").with(csrf()));

		mvc.perform(post("/todos/clear-completed").param("filter", "ACTIVE").with(csrf())).andExpect(redirectedUrl("/todos?filter=ACTIVE"));
		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("keep");
	}

	@Test
	void editRenamesTodo() throws Exception {
		Todo todo = repository.save(new Todo("old"));
		mvc.perform(get("/todos/{id}/edit", todo.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"old\"")));
		mvc.perform(post("/todos/{id}", todo.getId()).param("title", "  new  ").with(csrf()))
			.andExpect(redirectedUrl("/todos"));
		assertThat(repository.findById(todo.getId()).orElseThrow().getTitle()).isEqualTo("new");
	}

	@Test
	void editWithBlankTitleKeepsOldTitle() throws Exception {
		Todo todo = repository.save(new Todo("old"));
		mvc.perform(post("/todos/{id}", todo.getId()).param("title", "").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/edit"))
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.findById(todo.getId()).orElseThrow().getTitle()).isEqualTo("old");
	}

	@Test
	void unknownIdReturns404() throws Exception {
		mvc.perform(get("/todos/{id}/edit", 999999)).andExpect(status().isNotFound());
		mvc.perform(post("/todos/{id}/completed", 999999).param("completed", "true").with(csrf())).andExpect(status().isNotFound());
		mvc.perform(post("/todos/{id}/delete", 999999).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void invalidFilterReturns400() throws Exception {
		mvc.perform(get("/todos").param("filter", "BOGUS"))
			.andExpect(status().isBadRequest())
			.andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentTypeMismatchException.class));
	}

	@Test
	void postWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/todos").param("title", "forged")).andExpect(status().isForbidden());
		assertThat(repository.count()).isZero();
	}

	@Test
	void everyFormCarriesCsrfToken() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		assertEveryFormHasCsrfToken(mvc.perform(get("/todos")).andReturn().getResponse().getContentAsString(), 4);
		assertEveryFormHasCsrfToken(mvc.perform(get("/todos/{id}/edit", todo.getId())).andReturn().getResponse().getContentAsString(), 1);
	}

	private static void assertEveryFormHasCsrfToken(String html, int expectedForms) {
		String[] parts = html.split("(?i)<form", -1);
		assertThat(parts.length - 1).isEqualTo(expectedForms);
		for (int i = 1; i < parts.length; i++) {
			String form = parts[i].substring(0, parts[i].toLowerCase().indexOf("</form>"));
			assertThat(form).contains("method=\"post\"").containsPattern("type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"");
		}
	}

	@Test
	void requestWithForeignHostHeaderIsRejected() throws Exception {
		for (String host : new String[] { "evil.example", "localhostx", "ost", "127.0.0.2", "evillocalhost", "127x0x0x1", "127.1", "localhost." }) {
			mvc.perform(get("/todos").header("Host", host)).andExpect(status().isBadRequest());
		}
		for (String host : new String[] { "localhost:8080", "127.0.0.1", "127.0.0.1:8080" }) {
			mvc.perform(get("/todos").header("Host", host)).andExpect(status().isOk());
		}
	}

	@Test
	void titleIsHtmlEscaped() throws Exception {
		repository.save(new Todo("<script>alert(1)</script>"));
		mvc.perform(get("/todos"))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
	}
}
