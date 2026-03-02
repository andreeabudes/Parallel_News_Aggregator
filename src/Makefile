SRC_DIR = .
MAIN_CLASS = Tema1
LIBS = ../libs/*
ARGS ?=

SOURCES := $(shell find $(SRC_DIR) -name "*.java")

build:
	javac -cp "$(LIBS):." -d . $(SOURCES)

run:
	java -cp "$(LIBS):." $(MAIN_CLASS) $(ARGS)

clean:
	find . -name "*.class" -delete