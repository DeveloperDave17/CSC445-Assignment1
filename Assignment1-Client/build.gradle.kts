plugins {
   id("java")
   id("application")
}

repositories {

}

dependencies {

}

application {
   mainClass = "edu.oswego.cs.PerformanceClient"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "edu.oswego.cs.HeatPropagationSimulation"
    }
}