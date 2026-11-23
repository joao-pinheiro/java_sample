package com.example.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TodoForm(@NotBlank @Size(max = 200) String title) {
}
