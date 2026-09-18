plugins {
    `kotlin-dsl`
}


gradlePlugin {
    plugins {
        register("kmpLibraryConvention") {
            id = "io.github.julystar.musicapp.convention.kmp.library"
            implementationClass = "io.github.julystar.musicapp.buildlogic.KmpLibraryConventionPlugin"
            displayName = "MusicApp KMP library conventions"
            description = "Base Kotlin Multiplatform library settings for all MusicApp modules."
        }
        register("cmpLibraryConvention") {
            id = "io.github.julystar.musicapp.convention.cmp.library"
            implementationClass = "io.github.julystar.musicapp.buildlogic.CmpLibraryConventionPlugin"
            displayName = "MusicApp CMP library conventions"
            description = "Compose Multiplatform shared presentation settings."
        }
                register("kmpDomainConvention") {
            id = "io.github.julystar.musicapp.convention.kmp.domain"
            implementationClass = "io.github.julystar.musicapp.buildlogic.KmpDomainConventionPlugin"
            displayName = "MusicApp KMP domain conventions"
            description = "Pure Kotlin domain module conventions (no Compose)."
        }
        register("musicSourceConvention") {
            id = "io.github.julystar.musicapp.convention.music-source"
            implementationClass = "io.github.julystar.musicapp.buildlogic.MusicSourceConventionPlugin"
            displayName = "MusicApp music source conventions"
            description = "Music source implementation module conventions."
        }

        register("featureConvention") {
            id = "io.github.julystar.musicapp.convention.feature"
            implementationClass = "io.github.julystar.musicapp.buildlogic.FeatureConventionPlugin"
            displayName = "MusicApp feature conventions"
            description = "Feature module conventions: CMP + Koin + Navigation + Immutable collections."
        }
        register("roomConvention") {
            id = "io.github.julystar.musicapp.convention.room"
            implementationClass = "io.github.julystar.musicapp.buildlogic.RoomConventionPlugin"
            displayName = "MusicApp Room conventions"
            description = "Room KSP and schema directory conventions."
        }
        register("cargoUniffiConvention") {
            id = "io.github.julystar.musicapp.convention.cargo-uniffi"
            implementationClass = "io.github.julystar.musicapp.buildlogic.CargoUniffiConventionPlugin"
            displayName = "MusicApp Cargo + UniFFI conventions"
            description = "Gobley Cargo and UniFFI build conventions for the shared module."
        }
        register("desktopReleaseConvention") {
            id = "io.github.julystar.musicapp.convention.desktop-release"
            implementationClass = "io.github.julystar.musicapp.buildlogic.DesktopReleaseConventionPlugin"
            displayName = "MusicApp Desktop release conventions"
            description = "Optimizes packaged Desktop images and installers."
        }
    }
}
