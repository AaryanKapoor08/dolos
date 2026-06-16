/*
 * dolos.spring-conventions
 * For Spring Boot services. Builds on dolos.java-conventions and adds the Spring Boot
 * plugin + Spring's dependency-management (which auto-imports the spring-boot-dependencies
 * BOM, so starter versions are managed and omitted from module builds).
 *
 * The Spring Cloud BOM is intentionally NOT imported here yet — it is added when the
 * first Spring Cloud consumer appears. Early services only need Spring Boot + their
 * specific starters.
 */
plugins {
    id("dolos.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
