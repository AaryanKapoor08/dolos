/*
 * bff-service — the GraphQL Backend-for-Frontend (Phase 5D).
 *
 * One aggregating GraphQL endpoint (/graphql, + the GraphiQL explorer) shaped for the Investigator
 * Console: a single query returns an alert together with its case, transactions, and account graph
 * neighbourhood, so a screen makes one round trip instead of fanning out itself. REACTIVE (WebFlux):
 * resolvers call the business services over a load-balanced (lb://) WebClient and relay the caller's
 * bearer token downstream. Stateless — no database, no Flyway. Registers with Eureka + pulls centralized
 * config, and is a Keycloak resource server (GraphiQL is left open; /graphql needs a token) like the
 * rest of the platform.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-common"))

    // Spring Cloud edge (Phase 5A): register with Eureka (enables lb:// WebClient) + pull config. The
    // Eureka client brings spring-cloud-loadbalancer, which resolves lb:// for the reactive WebClient.
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.spring.cloud.starter.config)

    // Shared reactive resource-server security (Keycloak realm roles -> ROLE_*); this module opens GraphiQL.
    implementation(project(":libs:dolos-security"))

    // Reactive web + Spring for GraphQL (schema-first; brings the GraphiQL explorer).
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("io.projectreactor:reactor-test")
}
