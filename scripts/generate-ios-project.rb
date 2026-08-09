#!/usr/bin/env ruby

require "fileutils"
require "open3"
require "xcodeproj"

root = File.expand_path("..", __dir__)
ios_app_dir = File.join(root, "iosApp")
project_path = File.join(ios_app_dir, "App.xcodeproj")
gradle_properties = File.read(File.join(root, "gradle.properties"))
app_version_base = gradle_properties.match(/^appVersionBase=(\d+\.\d+\.\d+)$/)&.[](1)
raise "gradle.properties must define appVersionBase as X.Y.Z" unless app_version_base

def git_output(root, *arguments)
  output, status = Open3.capture2("git", *arguments, chdir: root)
  status.success? ? output.strip : ""
end

commit_count = [git_output(root, "rev-list", "--count", "HEAD").to_i, 1].max
commit_sha = git_output(root, "rev-parse", "--short=12", "HEAD")
tagged_version = git_output(root, "tag", "--points-at", "HEAD")
  .lines
  .map(&:strip)
  .map { |tag| tag[/\Av(\d+\.\d+\.\d+)\z/, 1] }
  .compact
  .first
tagged_version ||= git_output(root, "tag", "--points-at", "HEAD")
  .lines
  .map(&:strip)
  .map { |tag| tag[/\Apre-v(\d+\.\d+\.\d+-beta\.\d+)\z/, 1] }
  .compact
  .first
app_version_name = ENV["APP_VERSION_NAME"]&.strip
app_version_name = nil if app_version_name&.empty?
app_version_name ||= tagged_version || "#{app_version_base}-dev.#{commit_count}+#{commit_sha}"
app_version_code = ENV["APP_VERSION_CODE"]&.match?(/\A[1-9]\d*\z/) ?
  ENV["APP_VERSION_CODE"] :
  commit_count.to_s
package_version = if app_version_name.include?("-dev.")
  major, minor = app_version_base.split(".")
  "#{major}.#{minor}.#{[app_version_code.to_i, 65_535].min}"
else
  app_version_name.split(/[+-]/).first
end

FileUtils.rm_rf(project_path)

project = Xcodeproj::Project.new(project_path)
project.root_object.attributes["LastSwiftUpdateCheck"] = "2640"
project.root_object.attributes["LastUpgradeCheck"] = "2640"

target = project.new_target(:application, "App", :ios, "16.0")
target.product_name = "TidePlayer"
target.product_reference.name = "TidePlayer.app"
target.product_reference.path = "TidePlayer.app"

app_group = project.main_group.new_group("App")
swift_file = app_group.new_file("AppMain.swift")
audio_tap_file = app_group.new_file("TideDspAudioTap.m")
app_group.new_file("TideDspAudioTap.h")
plist_file = app_group.new_file("Info.plist")
app_group.new_file("App.entitlements")
assets_file = app_group.new_file("Assets.xcassets")
target.add_file_references([swift_file, audio_tap_file])
target.resources_build_phase.add_file_reference(assets_file)

target.build_configurations.each do |configuration|
  settings = configuration.build_settings
  settings["ASSETCATALOG_COMPILER_APPICON_NAME"] = "AppIcon"
  settings["CODE_SIGN_STYLE"] = "Automatic"
  settings["CODE_SIGN_ENTITLEMENTS"] = "App.entitlements"
  settings["CURRENT_PROJECT_VERSION"] = app_version_code
  settings["DEVELOPMENT_TEAM"] = ""
  settings["ENABLE_USER_SCRIPT_SANDBOXING"] = "NO"
  settings["EXCLUDED_ARCHS[sdk=iphonesimulator*]"] = "x86_64"
  settings["FRAMEWORK_SEARCH_PATHS"] = [
    "$(inherited)",
    "$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)",
  ]
  settings["GENERATE_INFOPLIST_FILE"] = "NO"
  settings["INFOPLIST_FILE"] = "$(SRCROOT)/Info.plist"
  settings["IPHONEOS_DEPLOYMENT_TARGET"] = "16.0"
  settings["MARKETING_VERSION"] = package_version
  settings["OTHER_LDFLAGS"] = [
    "$(inherited)",
    "-framework",
    "SharedKit",
    "-framework",
    "AudioToolbox",
    "-framework",
    "AVFoundation",
    "-framework",
    "MediaToolbox",
    "-framework",
    "CoreMedia",
  ]
  settings["PRODUCT_BUNDLE_IDENTIFIER"] = "io.github.julystar.musicapp"
  settings["PRODUCT_NAME"] = "TidePlayer"
  settings["SUPPORTED_PLATFORMS"] = "iphoneos iphonesimulator"
  settings["SWIFT_VERSION"] = "6.0"
  settings["TARGETED_DEVICE_FAMILY"] = "1,2"
end

kotlin_phase = target.new_shell_script_build_phase("Compile Kotlin Framework")
kotlin_phase.shell_path = "/bin/sh"
kotlin_phase.always_out_of_date = "1"
kotlin_phase.shell_script = <<~SH
  set -e
  cd "$SRCROOT/.."
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode
SH
target.build_phases.move(kotlin_phase, 0)

project.save

scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(project_path, "App", true)
