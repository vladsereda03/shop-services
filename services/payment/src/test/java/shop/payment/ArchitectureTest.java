package shop.payment;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforced at build time: they run with the plain unit tests (no Spring context,
 * no Docker) and fail the build on a violation, so the conventions below cannot erode silently.
 *
 * <p>Written as plain JUnit tests on purpose, not as {@code @ArchTest} fields: Maven Surefire
 * 3.5.3+ silently drops the archunit-junit5 engine's field-based tests — the build stays green with
 * "Tests run: 0" (TNG/ArchUnit#1442). Plain Jupiter tests cannot disappear unnoticed.
 */
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("shop.payment");
  }

  /**
   * Web layer calls the service layer, the service layer calls repositories — never around the
   * service layer and never in reverse. Layers are deliberately non-optional: if a package rename
   * empties a layer, the rule fails instead of silently checking nothing.
   */
  @Test
  void layeredArchitectureIsRespected() {
    layeredArchitecture()
        .consideringAllDependencies()
        .layer("Controller")
        .definedBy("..controller..")
        .layer("Service")
        .definedBy("..service..")
        .layer("Repository")
        .definedBy("..repository..")
        .whereLayer("Controller")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Service")
        .mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Repository")
        .mayOnlyBeAccessedByLayers("Service")
        .check(classes);
  }

  /** Constructor injection only: keeps dependencies explicit, final and testable without Spring. */
  @Test
  void noFieldInjection() {
    NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
  }

  /** All output goes through SLF4J, never System.out/err or printStackTrace. */
  @Test
  void noStandardStreams() {
    NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
  }
}
