#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


class GradlePatchError(ValueError):
    pass


def patch_gradle_source(source: str) -> str:
    anchor = '''    unitTest {
        enable()
        testedMod.set(mods.named("skyforge"))
    }
'''
    if source.count(anchor) != 1:
        raise GradlePatchError(f"v0.20 Gradle run anchor expected once, found {source.count(anchor)}")

    injected = '''        // AIRCRAFT-001 v0.20 disposable actual-client qualification. These runs are patched into
        // CI only; production/runtime build configuration remains unchanged until the boundary passes.
        create("aircraftCompilerPilotWorldPrepareServer") {
            server()
            sourceSet.set(waveC11Runtime)
            gameDirectory = layout.projectDirectory.dir("run-aircraft-compiler-pilot-client").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("aircraft-compiler-pilot-client")
            systemProperty("skyforge.dev.aircraftCompilerPilotWorldPrepare", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "aircraft-001-v020-world-prepare")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "120")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/aircraft-001-v020/prepare.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("aircraftCompilerPilotClient") {
            client()
            sourceSet.set(waveC11Runtime)
            gameDirectory = layout.projectDirectory.dir("run-aircraft-compiler-pilot-client").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("aircraft-compiler-pilot-client")
            systemProperty("skyforge.dev.aircraftCompilerPilotClient", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "aircraft-001-v020-pilot-client")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "180")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/aircraft-001-v020/client.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

'''
    return source.replace(anchor, injected + anchor, 1)


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch disposable AIRCRAFT-001 v0.20 exact-stack client runs into ModDevGradle")
    parser.add_argument("gradle_source", type=Path)
    args = parser.parse_args()
    source = args.gradle_source.read_text(encoding="utf-8")
    args.gradle_source.write_text(patch_gradle_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
