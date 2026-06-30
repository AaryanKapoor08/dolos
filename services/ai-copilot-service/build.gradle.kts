/*
 * ai-copilot-service — the AI copilot (Phase 4).
 *
 * A Spring AI application backed by a local Ollama model ($0 infra). 4A wires a ChatClient over
 * Ollama and exposes POST /api/copilot/chat. Later steps add RAG (MinIO + Tika + pgvector, 4B/4C),
 * tool-calling into the platform (4D), an investigate-alert agent (4E) and an MCP server (4F) — all
 * from the same Spring AI BOM. The chat/embedding model + Ollama endpoint are single Spring properties
 * (application.yml), so swapping to a cloud model later is a config change, not a code change.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    // Align all spring-ai-* artifacts (Boot 3.4 does not manage Spring AI itself).
    implementation(platform(libs.spring.ai.bom))

    implementation(project(":libs:dolos-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI Ollama chat model: auto-configures a ChatClient.Builder pointed at the configured
    // Ollama endpoint (spring.ai.ollama.* in application.yml). The starter also brings the embedding
    // model auto-config used from 4B onwards.
    implementation(libs.spring.ai.starter.model.ollama)

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
