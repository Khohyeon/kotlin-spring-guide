package com.example.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.repository.findByIdOrNull

@DataJpaTest
class RepositoriesTests @Autowired constructor(
        val entityManager: TestEntityManager,
        val userRepository: UserRepository,
        val articleRepository: ArticleRepository) {

  @Test
  fun `findByIdOrNull Article Test`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    entityManager.persist(johnDoe)
    val article = Article("Lorem", "Lorem", "dolor sit amet", johnDoe)
    entityManager.persist(article)
    entityManager.flush()
    val found = articleRepository.findByIdOrNull(article.id!!)
    assertThat(found).isEqualTo(article)
  }

  @Test
  fun `findByLogin then return User`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    entityManager.persist(johnDoe)
    entityManager.flush()
    val user = userRepository.findByLogin(johnDoe.login)
    assertThat(user).isEqualTo(johnDoe)
  }
}