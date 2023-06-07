plugins {
    id("java")
}

group = "ru.example.plugin"
version = "1.0.0"

val projectsNameArtifact = "${project.name}.jar"
// Версия mindustry, под которую сделан плагин
val mindustryVersion = "v144.3"

repositories {
    mavenCentral()
    // На Jitpack находятся библиотеки от Anuken'а
    maven("https://www.jitpack.io")
}

dependencies {
    // compileOnly используется для компиляции нашего проекта не включая библиотеки игры в выходной jar
    compileOnly("com.github.Anuken.Arc:arc-core:$mindustryVersion")
    // MindustryJitpack имеет небольшую историю git, следовательно, меньше весит
    compileOnly("com.github.Anuken.MindustryJitpack:core:$mindustryVersion")
}

// Копируем выходной jar файл в директорию с нашим сервером для быстрого тестирования плагина
val copyTask = tasks.register<Copy>("copyToServer") {
    from("$buildDir/libs/$projectsNameArtifact")
    // Не забудьте выставить переменную окружающей среды SERVER_MODS на папку с модификациями вашего сервера
    into(System.getenv("SERVER_MODS"))
}

tasks.withType<JavaCompile> {
    // Устанавливаем совместимость с 16 версии Java
    val javaVersion = "16"
    // Используем данную настройку для переваривания текста на кириллице
    options.encoding = "UTF-8"
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.jar {
    archiveFileName.set(projectsNameArtifact)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        it.takeIf { it.isDirectory } ?: zipTree(it)
    })
    finalizedBy(copyTask)
}