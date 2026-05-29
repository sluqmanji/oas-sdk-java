package egain.oassdk.generators.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generates the {@code egain.framework.validation} runtime framework that the generated
 * parameter validators build on — the {@link ValidatorAction}-style SPI, the {@code Validator}
 * engine, the fluent builders, the {@code ValidationError} model, and the {@code L10NResource}
 * data holder in the {@code egain.framework.validation.data} subpackage.
 *
 * <p>Like {@code RequestInfo} / {@code Validations}, these classes live in fixed packages that the
 * generated validators import directly, so they are emitted unconditionally (in both full and
 * models-only modes) by the orchestrator.
 */
class JerseyValidationFrameworkGenerator {

    private final JerseyGenerationContext ctx;

    JerseyValidationFrameworkGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Generate every framework class into the {@code egain/framework/validation} (and
     * {@code .../data}) directories under the output source root.
     */
    void generate() throws IOException {
        if (ctx.outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String sourceRoot = ctx.outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/");
        String validationDir = sourceRoot + "egain/framework/validation";
        String dataDir = validationDir + "/data";

        Files.createDirectories(Paths.get(dataDir));

        generateValidatorAction(validationDir);
        generateValidator(validationDir);
        generateValidationBuilder(validationDir);
        generateValidationError(validationDir);
        generateValidationErrorBuilder(validationDir);
        generateValidationErrorHelper(validationDir);
        generateL10NResource(dataDir);
    }

    private void generateValidatorAction(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                public interface ValidatorAction<T>
                {
                \tValidationError call(T input);
                }
                """;
        writeFile(dir + "/ValidatorAction.java", content);
    }

    private void generateValidator(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                import java.util.ArrayList;
                import java.util.List;

                public class Validator<T>
                {

                \tprivate final List<ValidatorAction<T>> validatorActions = new ArrayList<>();

                \tprotected Validator()
                \t{

                \t}

                \tprotected void add(ValidatorAction<T> validatorAction)
                \t{
                \t\tvalidatorActions.add(validatorAction);
                \t}

                \tpublic List<ValidationError> validate(T input)
                \t{
                \t\tList<ValidationError> validationErrors = new ArrayList<>();
                \t\tfor (ValidatorAction<T> validatorAction : validatorActions)
                \t\t{
                \t\t\tValidationError validationError = validatorAction.call(input);
                \t\t\tif (validationError != null)
                \t\t\t{
                \t\t\t\tvalidationErrors.add(validationError);
                \t\t\t\tbreak;
                \t\t\t}
                \t\t}
                \t\treturn validationErrors;
                \t}
                }
                """;
        writeFile(dir + "/Validator.java", content);
    }

    private void generateValidationBuilder(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                public class ValidationBuilder<T>
                {
                \tprivate final Validator<T> validator = new Validator<>();

                \tpublic ValidationBuilder<T> add(ValidatorAction<T> validatorAction)
                \t{
                \t\tvalidator.add(validatorAction);
                \t\treturn this;
                \t}

                \tpublic Validator<T> build()
                \t{
                \t\treturn validator;
                \t}
                }
                """;
        writeFile(dir + "/ValidationBuilder.java", content);
    }

    private void generateValidationError(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Locale;

                import egain.framework.validation.data.L10NResource;

                public class ValidationError
                {
                \tprivate final String l10nKey;

                \tpublic List<L10NResource> getErrorArguments() {
                \t\treturn errorArguments;
                \t}

                \tprivate final List<L10NResource> errorArguments = new ArrayList<>();

                \tpublic String getL10nKey() {
                \t\treturn l10nKey;
                \t}

                \tprotected ValidationError(String l10nKey)
                \t{
                \t\tthis.l10nKey = l10nKey;
                \t}

                \tprotected void argument(String argument)
                \t{
                \t\terrorArguments.add(new L10NResource(argument));
                \t}

                \tprotected void localizedArgument(String l10nFilePath, String localizedArgument)
                \t{
                \t\terrorArguments.add(new L10NResource(l10nFilePath, localizedArgument, true));
                \t}
                }
                """;
        writeFile(dir + "/ValidationError.java", content);
    }

    private void generateValidationErrorBuilder(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                public class ValidationErrorBuilder
                {
                \tprivate final ValidationError validationError;
                \t
                \tpublic ValidationErrorBuilder(String l10nKey)
                \t{
                \t\tvalidationError = new ValidationError(l10nKey);
                \t}
                \t
                \tpublic ValidationError build()
                \t{
                \t\treturn validationError;
                \t}
                \t
                \tpublic ValidationErrorBuilder argument(String argument)
                \t{
                \t\tvalidationError.argument(argument);
                \t\treturn this;
                \t}
                \t
                \tpublic ValidationErrorBuilder localizedArgument(String l10nFilePath, String localizedArgument)
                \t{
                \t\tvalidationError.localizedArgument(l10nFilePath, localizedArgument);
                \t\treturn this;
                \t}
                }
                """;
        writeFile(dir + "/ValidationErrorBuilder.java", content);
    }

    private void generateValidationErrorHelper(String dir) throws IOException {
        String content = """
                package egain.framework.validation;

                import java.util.List;

                public class ValidationErrorHelper
                {
                \tpublic static ValidationError createValidationError(String l10nFilePath, String l10NKey, List<String> arguments, List<String> localizedArgs)
                \t{
                \t\tValidationError validationError = new ValidationError(l10NKey);
                \t\targuments.forEach(validationError::argument);
                \t\tlocalizedArgs.forEach(localizedArg -> validationError.localizedArgument(l10nFilePath, localizedArg));
                \t\treturn validationError;
                \t}
                }
                """;
        writeFile(dir + "/ValidationErrorHelper.java", content);
    }

    private void generateL10NResource(String dir) throws IOException {
        String content = """
                package egain.framework.validation.data;

                /**
                 * This class contains information to localize a L10N key.
                 *
                 */
                public class L10NResource
                {
                \tprivate String l10nFilePath;
                \tprivate final String l10nKey;

                \t// set to true if 'key' should be localized, else false
                \tprivate boolean isLocalize = false;

                \tpublic L10NResource(String l10nFilePath, String l10nKey, boolean isLocalize)
                \t{
                \t\tthis.l10nFilePath = l10nFilePath;
                \t\tthis.l10nKey = l10nKey;
                \t\tthis.isLocalize = isLocalize;
                \t}

                \t/**
                \t * Use this constructor whenever using this Data object to populate a non-localized string. E.g.: Comma separated String of
                \t * activity IDs. Since <code>WsErrorProducer</code> requires an array of <code>L10NResourceData</code> objects to be
                \t * provided, it is quite possible that some of these are localized and some others are not. Using this constructor is same
                \t * as using <code>L10NResourceData(String l10nFilePath, String l10nKey, boolean
                \t * isLocalize)</code> with isLocalise as false.
                \t *
                \t * @param l10nKey:
                \t *            String to be used.
                \t */
                \tpublic L10NResource(String l10nKey)
                \t{
                \t\tthis.l10nKey = l10nKey;
                \t}

                \t/**
                \t * @return the l10nFilePath
                \t */
                \tpublic String getL10nFilePath()
                \t{
                \t\treturn l10nFilePath;
                \t}

                \t/**
                \t * @return the l10nKey
                \t */
                \tpublic String getL10nKey()
                \t{
                \t\treturn l10nKey;
                \t}

                \t/**
                \t * @return the isLocalize
                \t */
                \tpublic boolean isLocalize()
                \t{
                \t\treturn isLocalize;
                \t}
                }
                """;
        writeFile(dir + "/L10NResource.java", content);
    }

    private void writeFile(String filePath, String content) throws IOException {
        JerseyGenerationContext.writeFile(filePath, content);
    }
}
