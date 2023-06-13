package com.example.kotlin.controller

import com.example.kotlin.module.ArticleRepository
import com.example.kotlin.module.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/article")
class ArticleController(private val repository: ArticleRepository) {

    @GetMapping("/")
    fun findAll() = repository.findAllByOrderByAddedAtDesc()

    @GetMapping("/{slug}")
    fun findOne(@PathVariable slug: String) = repository.findBySlug(slug) ?:
    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article 이 존재하지 않습니다.")

}

@RestController
@RequestMapping("/api/user")
class UserController(private val repository: UserRepository) {

    @GetMapping("/")
    fun findAll() = repository.findAll();

    @GetMapping("/{login}")
    fun findOne(@PathVariable login: String) = repository.findByLogin(login)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User 가 존재하지 않습니다.")
}