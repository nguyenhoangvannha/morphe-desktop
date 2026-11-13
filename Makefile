# ==============================================================================
# Morphe Desktop - Makefile
# ==============================================================================

VERSION ?= $(shell grep -E '^version\s*=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
ARCH    ?= $(shell uname -m | sed -e 's/x86_64/x64/')
ARGS    ?=

BUILD_DIR  := build
LIBS_DIR   := $(BUILD_DIR)/libs
MACOS_DIR  := $(BUILD_DIR)/macos

JAR        := $(LIBS_DIR)/morphe-desktop-$(VERSION)-all.jar
APP        := $(MACOS_DIR)/Morphe.app
ZIP        := $(MACOS_DIR)/morphe-desktop-$(VERSION)-macos-$(ARCH).app.zip
DMG        := $(MACOS_DIR)/morphe-desktop-$(VERSION)-macos-$(ARCH).dmg

GRADLEW    := ./gradlew
SHELL      := /bin/bash

.PHONY: all help jar jar-fast app macos dmg run run-app test clean install

# Default target
all: macos

## help: Display this list of available targets
help:
	@echo "Morphe Desktop Build System (v$(VERSION))"
	@echo ""
	@echo "Usage: make [target] [ARGS=\"...\"]"
	@echo ""
	@echo "Targets:"
	@echo "  help         Show this help message"
	@echo "  jar          Build production fat JAR with ProGuard minification"
	@echo "  jar-fast     Build fat JAR without ProGuard (faster, for development)"
	@echo "  macos (app)  Build standalone macOS .app bundle and .app.zip distribution"
	@echo "  dmg          Build macOS .dmg installer image with /Applications shortcut"
	@echo "  run          Run the built runnable fat JAR (pass arguments with ARGS=\"...\")"
	@echo "  run-app      Launch the packaged macOS Morphe.app"
	@echo "  test         Run test suite via Gradle"
	@echo "  clean        Clean build directories and artifacts"
	@echo "  install      Install Morphe.app into /Applications"
	@echo ""
	@echo "Environment:"
	@echo "  VERSION:     $(VERSION)"
	@echo "  ARCH:        $(ARCH)"
	@echo "  JAR:         $(JAR)"
	@echo "  APP:         $(APP)"

## jar: Build production fat JAR (with ProGuard)
jar:
	@bash scripts/build-jar.sh

## jar-fast: Fast build without ProGuard minification
jar-fast:
	@bash scripts/build-jar.sh --fast

## macos: Build macOS application bundle (.app) and .app.zip archive
macos: $(JAR)
	@bash scripts/package-macos.sh "$(VERSION)" "$(JAR)"

## app: Alias for macos
app: macos

## dmg: Package macOS app into DMG installer image
dmg: macos
	@bash scripts/package-dmg.sh "$(VERSION)"

## run: Run the fat JAR with optional ARGS
run: $(JAR)
	@echo "==> Running $(JAR)..."
	@java -jar "$(JAR)" $(ARGS)

## run-app: Launch the macOS application bundle
run-app: $(APP)
	@echo "==> Launching $(APP)..."
	@open "$(APP)"

## test: Run unit tests
test:
	@echo "==> Running tests..."
	@$(GRADLEW) test

## clean: Remove all build outputs
clean:
	@echo "==> Cleaning build artifacts..."
	@$(GRADLEW) clean
	@rm -rf $(MACOS_DIR)
	@echo "==> Clean complete."

## install: Install Morphe.app to /Applications
install: $(APP)
	@echo "==> Installing Morphe.app to /Applications..."
	@rm -rf "/Applications/Morphe.app"
	@cp -R "$(APP)" "/Applications/Morphe.app"
	@echo "==> Installed Morphe.app to /Applications"

# Ensure JAR exists before packaging macOS app
$(JAR):
	@bash scripts/build-jar.sh

# Ensure APP exists before running
$(APP):
	@bash scripts/package-macos.sh "$(VERSION)" "$(JAR)"
