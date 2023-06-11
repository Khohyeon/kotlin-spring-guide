### JUnit 5로 테스트


#### 통합 테스트
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTests(@Autowired val restTemplate: TestRestTemplate) {

  @Test
  fun `Assert blog page title, content and status code`() {
    val entity = restTemplate.getForEntity<String>("/")
    assertThat(entity.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(entity.body).contains("<h1>Blog</h1>")
  }

}
```

```
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

SpringBoot 애플리케이션을 테스트하기 위해 웹 서버를 무작위 포트로 실행합니다.

#### 무작위 포트로 실행을 하는 이유는?
- 테스트를 병렬로 실행할 때 포트 충돌을 방지하기 위해
- 테스트를 반복적으로 실행할 때 이전 테스트의 결과가 남아있어서 테스트에 영향을 주는 것을 방지하기 위해

#### TestRestTemplate
- 보통 실제 운영 환경에서 HTTP 요청과 응답을 처리하는데 사용되는 라이브 러리 RestTemplate 을 사용합니다.
- TestRestTemplate 은 테스트 환경에서 RestTemplate 을 사용할 수 있도록 해줍니다.
- 운영 환경에서 HTTP 요청을 보내고 응답을 처리해야 한다면 RestTemplate 을 사용하고, 테스트 환경에서 HTTP 요청을 테스트하고 응답을 검증해야 한다면 TestRestTemplate 을 사용하는 것이 일반적인 패턴입니다.

#### 테스트에 properties 추가하기
src/test/resources/junit-platform.properties
```
junit.jupiter.testinstance.lifecycle.default = per_class
```
일반 메서드에 @BeforeAll 과 @AfterAll 어노테이션을 사용할 수 있도록 해줍니다.

### 나만의 확장 프로그램 만들기
Java와 같이 추상 메서드와 함께 util 클래스를 사용하는 대신 Kotlin 에서는 Kotlin 확장을 통해 이러한 기능을 제공하는 것이 일반적입니다. 여기에서는 영어 날짜 형식으로 텍스트를 생성하기 위해 기존 LocalDateTime 유형에 format() 함수를 추가할 것입니다.

#### Extensions.kt
```kotlin
fun LocalDateTime.format(): String = this.format(englishDateFormatter)

private val daysLookup = (1..31).associate { it.toLong() to getOrdinal(it) }

private val englishDateFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd")
    .appendLiteral(" ")
    .appendText(ChronoField.DAY_OF_MONTH, daysLookup)
    .appendLiteral(" ")
    .appendPattern("yyyy")
    .toFormatter(Locale.ENGLISH)

private fun getOrdinal(n: Int) = when {
  n in 11..13 -> "${n}th"
  n % 10 == 1 -> "${n}st"
  n % 10 == 2 -> "${n}nd"
  n % 10 == 3 -> "${n}rd"
  else -> "${n}th"
}

fun String.toSlug() = lowercase(Locale.getDefault())
    .replace("\n", " ")
    .replace("[^a-z\\d\\s]".toRegex(), " ")
    .split(" ")
    .joinToString("-")
    .replace("-+".toRegex(), "-")
```

다음 섹션에서 이러한 확장을 활용할 것입니다.

### JPA와의 지속성
지연 가져오기가 예상대로 작동하도록 하려면 엔터티가 KT-28525에 설명된 대로 열려 있어야 합니다. 이를 위해 Kotlin allopen 플러그인을 사용할 것입니다.

#### 추가 gradle
```
plugins {
  ...
  kotlin("plugin.allopen") version "1.8.0"
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.Embeddable")
  annotation("jakarta.persistence.MappedSuperclass")
}
```

#### allopen plugin의 기능
- 모든 클래스와 인터페이스를 기본적으로 열린 클래스로 만듭니다.
- java 에서의 @Entity, @Embeddable, @MappedSuperclass 와 같은 어노테이션을 사용할 수 있도록 해줍니다.

### Entity 클래스 만들기
Entities.kt
```kotlin
@Entity
class Article(
    var title: String,
    var headline: String,
    var content: String,
    @ManyToOne var author: User,
    var slug: String = title.toSlug(),
    var addedAt: LocalDateTime = LocalDateTime.now(),
    @Id @GeneratedValue var id: Long? = null)

@Entity
class User(
    var login: String,
    var firstname: String,
    var lastname: String,
    var description: String? = null,
    @Id @GeneratedValue var id: Long? = null)
```
- kotlin 에서는 코틀린 파일에 Entity 를 위의 코드와 같이 정의할 수 있습니다.
- @Entity 어노테이션을 사용하여 JPA 에서 사용하는 엔터티임을 알려줍니다.
- @Id 어노테이션을 사용하여 id 필드가 엔터티의 기본 키임을 알려줍니다.
- @GeneratedValue 어노테이션을 사용하여 id 필드가 자동으로 생성되어야 함을 알려줍니다.
- @ManyToOne 어노테이션을 사용하여 Article 엔터티와 User 엔터티가 다대일 관계임을 알려줍니다.
- Article 생성자의 slug 매개변수에 대한 기본 인수를 제공하기 위해 String.toSlug() 확장을 사용합니다.

### Repository 만들기
Repositories.kt
```kotlin
interface ArticleRepository : JpaRepository<Article, Long> {
        fun findBySlug(slug: String): Article?
        fun findAllByOrderByAddedAtDesc(): List<Article>
}

interface UserRepository : JpaRepository<User, Long> {
        fun findByLogin(login: String): User?
        }
```

- ArticleRepository 와 UserRepository 는 각각 Article 과 User 엔터티를 위한 Spring Data Repository 인터페이스입니다.
- Spring Data 는 인터페이스의 메서드 이름을 분석하여 해당 메서드가 수행해야 하는 작업을 결정합니다.
- findBySlug() 메서드는 slug 필드를 기반으로 Article 을 검색합니다.
- findAllByOrderByAddedAtDesc() 메서드는 addedAt 필드를 기반으로 Article 을 검색합니다.

### RepsitoryTest 만들기
RepositoriesTest.kt
```kotlin
@DataJpaTest
class RepositoriesTests @Autowired constructor(
    val entityManager: TestEntityManager,
    val userRepository: UserRepository,
    val articleRepository: ArticleRepository) {

  @Test
  fun `When findByIdOrNull then return Article`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    entityManager.persist(johnDoe)
    val article = Article("Lorem", "Lorem", "dolor sit amet", johnDoe)
    entityManager.persist(article)
    entityManager.flush()
    val found = articleRepository.findByIdOrNull(article.id!!)
    assertThat(found).isEqualTo(article)
  }

  @Test
  fun `When findByLogin then return User`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    entityManager.persist(johnDoe)
    entityManager.flush()
    val user = userRepository.findByLogin(johnDoe.login)
    assertThat(user).isEqualTo(johnDoe)
  }
}
```

- @DataJpaTest 어노테이션을 사용하여 Spring Data JPA 를 사용하는 테스트를 만듭니다.
- @Autowired 어노테이션을 사용하여 테스트에 필요한 Spring Bean 을 주입합니다.
- TestEntityManager 는 JPA 테스트를 위한 특별한 EntityManager 입니다.
- 테스트에서는 EntityManager 를 직접 사용하여 엔터티를 저장하고 테스트 후에는 테스트 데이터를 삭제합니다.

### View 만들기
화면은 Mustache 템플릿을 사용하여 만들 것입니다. Mustache 는 템플릿 엔진으로 템플릿과 데이터를 결합하여 HTML 을 생성합니다.

#### header.mustache
```html
<html>
<head>
    <title>{{title}}</title>
</head>
<body>
```
- {{title}} 은 템플릿에 전달된 데이터의 title 속성을 참조합니다.

#### footer.mustache
```html
</body>
</html>
```

#### blog.mustache
```html
{{> header}}

<h1>{{title}}</h1>

<div class="articles">

  {{#articles}}
    <section>
      <header class="article-header">
        <h2 class="article-title"><a href="/article/{{slug}}">{{title}}</a></h2>
        <div class="article-meta">By  <strong>{{author.firstname}}</strong>, on <strong>{{addedAt}}</strong></div>
      </header>
      <div class="article-description">
        {{headline}}
      </div>
    </section>
  {{/articles}}
</div>

{{> footer}}
```
- {{> header}} 와 {{> footer}} 는 header.mustache 와 footer.mustache 파일을 참조합니다.
- {{#articles}} 와 {{/articles}} 는 articles 속성의 값이 배열이라는 것을 나타냅니다.<br>
-> articles 속성의 각 요소에 대해 템플릿을 반복합니다. (java 에서의 foreach 와 같은 용도) 
- {{author.firstname}} 은 author 속성의 firstname 속성을 참조합니다.
- {{addedAt}} 은 addedAt 속성의 값을 참조합니다.

#### article.mustache
```html
{{> header}}

<section class="article">
  <header class="article-header">
    <h1 class="article-title">{{article.title}}</h1>
    <p class="article-meta">By  <strong>{{article.author.firstname}}</strong>, on <strong>{{article.addedAt}}</strong></p>
  </header>

  <div class="article-description">
    {{article.headline}}

    {{article.content}}
  </div>
</section>

{{> footer}}
```

### Kotlin Test Mockk
테스트의 경우 통합 테스트 대신 Mockito와 유사하지만 Kotlin에 더 적합한 @WebMvcTest 및 Mockk를 활용할 것입니다.

@MockBean 및 @SpyBean 주석은 Mockito에만 해당되므로 Mockk에 대해 유사한 @MockkBean 및 @SpykBean 주석을 제공하는 SpringMockK를 활용할 것입니다.

#### 의존성 추가 (Mockk)
```
testImplementation("org.springframework.boot:spring-boot-starter-test") {
exclude(module = "mockito-core")
}
```
- Spring Boot Test 에서 mockito-core 를 제외합니다.
- 
```
testImplementation("org.junit.jupiter:junit-jupiter-api")
```
- JUnit 5를 사용하여 테스트를 작성하기 위한 필수적인 의존성입니다.
- JUnit 5는 최신 버전의 JUnit 프레임워크로, 테스트 작성과 실행을 위한 많은 기능을 제공합니다.

```
testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
```

- JUnit 5의 엔진(Engine) 구현체입니다.
- 이 엔진은 JUnit 5 테스트를 실행하는 데 사용됩니다.

```
testImplementation("com.ninja-squad:springmockk:4.0.0")
```

- Spring MockK는 MockK를 스프링 프레임워크와 통합하기 위한 라이브러리입니다.
- MockK를 사용하여 스프링 애플리케이션의 모의 객체(Mock Object)를 생성하고 관리하는 데 도움을 줍니다.
- 이 라이브러리는 MockK의 기능을 스프링 테스트에서 사용할 수 있도록 지원합니다.

#### HttpControllersTests.kt

```kotlin
@WebMvcTest
class HttpControllersTests(@Autowired val mockMvc: MockMvc) {

  @MockkBean
  lateinit var userRepository: UserRepository

  @MockkBean
  lateinit var articleRepository: ArticleRepository

  @Test
  fun `List articles`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    val lorem5Article = Article("Lorem", "Lorem", "dolor sit amet", johnDoe)
    val ipsumArticle = Article("Ipsum", "Ipsum", "dolor sit amet", johnDoe)
    every { articleRepository.findAllByOrderByAddedAtDesc() } returns listOf(lorem5Article, ipsumArticle)
    mockMvc.perform(get("/api/article/").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("\$.[0].author.login").value(johnDoe.login))
        .andExpect(jsonPath("\$.[0].slug").value(lorem5Article.slug))
        .andExpect(jsonPath("\$.[1].author.login").value(johnDoe.login))
        .andExpect(jsonPath("\$.[1].slug").value(ipsumArticle.slug))
  }

  @Test
  fun `List users`() {
    val johnDoe = User("johnDoe", "John", "Doe")
    val janeDoe = User("janeDoe", "Jane", "Doe")
    every { userRepository.findAll() } returns listOf(johnDoe, janeDoe)
    mockMvc.perform(get("/api/user/").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("\$.[0].login").value(johnDoe.login))
        .andExpect(jsonPath("\$.[1].login").value(janeDoe.login))
  }
}
```

- @WebMvcTest 는 스프링 MVC 컨트롤러를 테스트하기 위한 애노테이션입니다.
- @MockkBean 은 Mockk 라이브러리를 사용하여 스프링 빈을 모의 객체로 대체하는 데 사용됩니다.
- @Autowired 는 스프링이 관리하는 빈을 주입받기 위한 애노테이션입니다.
- mockMvc.perform(get("/api/article/").accept(MediaType.APPLICATION_JSON)) 는 /api/article/ 경로로 GET 요청을 보내는 것을 의미합니다.
- .andExpect(status().isOk) 는 응답의 상태 코드가 200(OK)인지 확인합니다.

```
@MockBean은 자바 기반의 테스트에서 사용됩니다.
@MockkBean은 코틀린 기반의 테스트에서 사용됩니다.
```

### 구성 속성
Kotlin 에서 애플리케이션 속성을 관리하는 권장 방법은 읽기 전용 속성을 사용하는 것입니다.

#### BlogProperties.kt

```kotlin
@ConfigurationProperties("blog")
data class BlogProperties(var title: String, val banner: Banner) {
  data class Banner(val title: String? = null, val content: String)
}
```
위의 생성한 BlogProperties 클래스는 blog.title 및 blog.banner.title 속성을 읽을 수 있습니다.

그리고 BlogApplication 클래스에 @EnableConfigurationProperties(BlogProperties::class) 를 추가하여 BlogProperties 클래스를 활성화합니다.
```kotlin
@SpringBootApplication
@EnableConfigurationProperties(BlogProperties::class)
class BlogApplication {
  // ...
}
```
IDE에서 이러한 사용자 지정 속성을 인식하기 위해 고유한 메타데이터를 생성하려면 다음과 같이 spring-boot-configuration-processor 종속성을 사용하여 kapt를 구성해야 합니다.

#### build.gradle.kts

```
plugins {
  ...
  kotlin("kapt") version "1.8.0"
}

dependencies {
  ...
  kapt("org.springframework.boot:spring-boot-configuration-processor")
}
```

자동 완성, 유효성 검사 등 편집할 때 사용자 정의 속성이 인식되어야 하기에 properties 에 추가해 줍니다. 

#### application.properties
```
blog.title=Blog
blog.banner.title=Warning
blog.banner.content=The blog will be down tomorrow.
```

#### 완성된 blog.mustache
```html
{{> header}}

<h1>{{title}}</h1>

<div class="articles">

    {{#banner.title}}
        <section>
            <header class="banner">
                <h2 class="banner-title">{{banner.title}}</h2>
            </header>
            <div class="banner-content">
                {{banner.content}}
            </div>
        </section>
    {{/banner.title}}

    {{#articles}}
        <section>
            <header class="article-header">
                <h2 class="article-title"><a href="/article/{{slug}}">{{title}}</a></h2>
                <div class="article-meta">By  <strong>{{author.firstname}}</strong>, on <strong>{{addedAt}}</strong></div>
            </header>
            <div class="article-headline">
                {{headline}}
            </div>
        </section>
    {{/articles}}
</div>
```

#### 완성된 HtmlController.kt
```kotlin
package com.example.kotlin

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

@Controller
class HtmlController(private val repository: ArticleRepository,
                     private val properties: BlogProperties) {

    @GetMapping("/")
    fun blog(model: Model): String {
        model["title"] = properties.title
        model["banner"] = properties.banner
        model["articles"] = repository.findAllByOrderByAddedAtDesc().map { it.render() }
        return "blog"
    }

    @GetMapping("/article/{slug}")
    fun article(@PathVariable slug: String, model: Model): String {
        val article = repository.findBySlug(slug)?.render()?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "article slug 가 존재하지 않습니다.")
        model["title"] = article.title
        model["article"] = article
        return "article"
    }

    fun Article.render() = RenderedArticle(
            slug,
            title,
            headline,
            content,
            author,
            addedAt.format()
    )

    data class RenderedArticle(
            val slug: String,
            val title: String,
            val headline: String,
            val content: String,
            val author: User,
            val addedAt: String
    )
}
```

