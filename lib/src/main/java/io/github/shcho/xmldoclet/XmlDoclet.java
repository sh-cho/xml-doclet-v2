package io.github.shcho.xmldoclet;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;

import io.github.shcho.utils.StringUtils;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option.Kind;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Doclet generating Javadoc as XML with
 * <a href="https://openjdk.org/groups/compiler/using-new-doclet.html">the new Doclet API</a>.
 */
public class XmlDoclet implements Doclet {

    // constants
    private static final boolean OK = true;
    private static final boolean ERROR = false;

    // defaults
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final String DEFAULT_OUTPUT_DIR = ".";
    private static final String DEFAULT_OUTPUT_FILENAME = "javadoc.xml";

    // internal
    private DocTrees docTrees;
    private Reporter reporter;

    // options
    private String outputDir = DEFAULT_OUTPUT_DIR;
    private String filename = DEFAULT_OUTPUT_FILENAME;
    private Boolean escapeCharacters = true;
    private final Set<Option> options = Set.of(
            // NOTE: this must be "-d" because of the Standard doclet
            new Option("-d", true,
                       "Destination directory for output file. (Default: " + DEFAULT_OUTPUT_DIR + ")",
                       "[<directory>]", Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    String outputDir = arguments.isEmpty() ? DEFAULT_OUTPUT_DIR : arguments.get(0);
                    outputDir = outputDir.replaceAll("/+$", "");  // remove trailing slashes
                    if (!Files.isDirectory(Paths.get(outputDir))) {
                        reporter.print(Diagnostic.Kind.ERROR, "Invalid output directory: " + outputDir);
                        return ERROR;
                    }

                    XmlDoclet.this.outputDir = outputDir;
                    return OK;
                }
            },
            new Option("-Xfilename", true, "Output filename. (Default: " + DEFAULT_OUTPUT_FILENAME + ")",
                       "[<filename>]", Kind.EXTENDED) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    final String filename = arguments.isEmpty() ? DEFAULT_OUTPUT_FILENAME : arguments.get(0);

                    XmlDoclet.this.filename = filename;
                    return OK;
                }
            },
            new Option("-Xescape", true, "Escape characters in javadoc comments\n"
                                         + "e.g. `-Xescape true`, \"안녕\" -> \"\\uc548\\ub155\"\n"
                                         + "     `-Xescape false`, \"안녕\" -> \"안녕\"\n"
                                         + "(Default: true)",
                       "[true|false]", Kind.EXTENDED) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    final String escape = arguments.isEmpty() ? "true" : arguments.get(0);

                    XmlDoclet.this.escapeCharacters = Boolean.parseBoolean(escape);
                    return OK;
                }
            }
    );
    /**
     * For now some options (ex. {@code -doctitle}, {@code -windowtitle}, etc.) are not needed for this Doclet,
     * but just allow it to make gradle happy.
     * <p>
     * Without this, gradle will fail with the error like below:
     * <pre>
     * error: option -doctitle not allowed
     * </pre>
     */
    private final Set<Option> ignoredOptions = Set.of(
            new Option("-doctitle", true, "IGNORED", "[<doctitle>]",
                       Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option " + option + " is ignored");
                    return OK;
                }
            },
            new Option("-windowtitle", true, "IGNORED", "[<windowtitle>]",
                       Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option " + option + " is ignored");
                    return OK;
                }
            },
            new Option("-notimestamp", false, "IGNORED", null, Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option " + option + " is ignored");
                    return OK;
                }
            });

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Stream.concat(options.stream(), ignoredOptions.stream())
                     .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment docEnv) {
        docTrees = docEnv.getDocTrees();

        // print runtime information
        reporter.print(Diagnostic.Kind.NOTE, "Output directory: " + outputDir);
        reporter.print(Diagnostic.Kind.NOTE, "Output filename: " + filename);
        reporter.print(Diagnostic.Kind.NOTE, "Escape characters: " + escapeCharacters);

        try (final OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputDir + '/' + filename), StandardCharsets.UTF_8)) {
            final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(writer);

            xmlWriter.writeStartDocument(DEFAULT_ENCODING, "1.0");
            xmlWriter.writeStartElement("root");

            for (PackageElement packageElement : ElementFilter.packagesIn(docEnv.getIncludedElements())) {
                processPackage(xmlWriter, packageElement);
            }

            xmlWriter.writeEndElement(); // root
            xmlWriter.writeEndDocument();
            xmlWriter.close();
        } catch (Exception e) {
            reporter.print(Diagnostic.Kind.ERROR, "Error generating XML: " + e.getMessage());
            return ERROR;
        }

        return OK;
    }

    private void processPackage(final XMLStreamWriter xmlWriter, final PackageElement packageElement)
            throws Exception {
        xmlWriter.writeStartElement("package");
        xmlWriter.writeAttribute("name", packageElement.getQualifiedName().toString());

        for (final TypeElement typeElement : ElementFilter.typesIn(packageElement.getEnclosedElements())) {
            processType(xmlWriter, typeElement);
        }

        xmlWriter.writeEndElement(); // package
    }

    private void processType(final XMLStreamWriter xmlWriter, final TypeElement typeElement) throws Exception {
        final String elementName = typeElement.getKind() == ElementKind.CLASS ? "class" : "interface";
        xmlWriter.writeStartElement(elementName);
        xmlWriter.writeAttribute("name", typeElement.getSimpleName().toString());
        xmlWriter.writeAttribute("qualified", typeElement.getQualifiedName().toString());

        processDocCommentTree(xmlWriter, docTrees.getDocCommentTree(typeElement));

        for (final VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            processField(xmlWriter, field);
        }

        xmlWriter.writeEndElement(); // class or interface
    }

    private void processField(final XMLStreamWriter xmlWriter, final VariableElement field) throws Exception {
        xmlWriter.writeStartElement("field");
        xmlWriter.writeAttribute("name", field.getSimpleName().toString());

        processDocCommentTree(xmlWriter, docTrees.getDocCommentTree(field));

        xmlWriter.writeEndElement();
    }

    private void processDocCommentTree(XMLStreamWriter xmlWriter, DocCommentTree docCommentTree)
            throws XMLStreamException {
        if (docCommentTree == null) {
            return;
        }

        xmlWriter.writeStartElement("comment");
        final String commentString = docCommentTree.toString();
        if (escapeCharacters) {
            xmlWriter.writeCharacters(commentString);
        } else {
            xmlWriter.writeCharacters(StringUtils.unescapeJavaString(commentString));
        }
        xmlWriter.writeEndElement(); // comment
    }

    abstract class Option implements Doclet.Option {

        private final String name;
        private final boolean hasArg;
        private final String description;
        private final String parameters;
        private final Kind kind;

        Option(String name, boolean hasArg, String description, String parameters, Kind kind) {
            this.name = name;
            this.hasArg = hasArg;
            this.description = description;
            this.parameters = parameters;
            this.kind = kind;
        }

        @Override
        public int getArgumentCount() {
            return hasArg ? 1 : 0;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public List<String> getNames() {
            return List.of(name);
        }

        @Override
        public String getParameters() {
            return hasArg ? parameters : "";
        }
    }
}

