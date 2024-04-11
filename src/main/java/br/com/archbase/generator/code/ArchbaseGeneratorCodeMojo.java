package br.com.archbase.generator.code;


import com.squareup.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import javax.lang.model.element.Modifier;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.javaparser.StaticJavaParser.parse;


@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class ArchbaseGeneratorCodeMojo
    extends AbstractMojo
{
    @Parameter(required = true)
    private String entityPackageBase;
    @Parameter(required = true)
    private String dtoOutputPackageBase;
    @Parameter(required = true)
    private String adapterOutputPackageBase;
    @Parameter(required = true)
    private String repositoryOutputPackageBase;
    @Parameter(required = true)
    private String persistenceOutputPackageBase;
    @Parameter(required = true)
    private String mapperOutputPackageBase;
    @Parameter(required = true)
    private String serviceOutputPackageBase;
    @Parameter(required = true)
    private String controllerOutputPackageBase;
    @Parameter(required = true)
    private String securityAdapterClassName;
    @Parameter(required = true)
    private List<ClassMapping> entityClasses;

    @Parameter(defaultValue = "${project.compileClasspathElements}")
    private List<String> classpathElements;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    protected List<URL> createClassPath() {
        List<URL> list = new ArrayList<>();
        if (classpathElements != null) {
            for (String cpel : classpathElements) {
                try {
                    list.add(new File(cpel).toURI().toURL());
                } catch (MalformedURLException mue) {
                }
            }
        }
        return list;
    }


    public void execute()
        throws MojoExecutionException
    {
        URLClassLoader urlClassLoader = new URLClassLoader(createClassPath().toArray(new URL[] {}),
                Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(urlClassLoader);

        for (ClassMapping classMapping : entityClasses) {
            Class<?> sourceClass = null;
            try {
                File outputDirectory = new File(project.getBasedir(), "src/main/java");
                sourceClass = urlClassLoader.loadClass(classMapping.baseClass);
                if (classMapping.generateDTO) {
                    generateDTO(classMapping, sourceClass, outputDirectory);
                }
                if (classMapping.generateRepository) {
                    generateRepository(classMapping, sourceClass, outputDirectory);
                }
                if (classMapping.generateMapper) {
                    generateMapper(classMapping, sourceClass, outputDirectory);
                }
                if (classMapping.generateAdapter) {
                    generateAdapter(classMapping, sourceClass, outputDirectory);
                }
                if (classMapping.generateService) {
                    generateService(classMapping, sourceClass, outputDirectory);
                }
                if (classMapping.generateController) {
                    generateController(classMapping, sourceClass, outputDirectory);
                }
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException(e);
            }
        }

    }



    private void generateController(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String controllerClassName = sourceClass.getSimpleName() + "Controller";
        ClassName sourceClassName = ClassName.get(sourceClass);
        ClassName sourceClassNameDTO = ClassName.get(dtoOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Dto");
        ClassName sourceClassNameService = ClassName.get(serviceOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Service");

        ClassName validationException = ClassName.get("br.com.archbase.validation.exception", "ArchbaseValidationException");
        ClassName archbaseAssert = ClassName.get("br.com.archbase.shared.kernel.utils", "ArchbaseAssert");
        ClassName responseEntity = ClassName.get("org.springframework.http", "ResponseEntity");
        ClassName httpStatus = ClassName.get("org.springframework.http", "HttpStatus");
        ClassName requestBody = ClassName.get("org.springframework.web.bind.annotation", "RequestBody");
        ClassName requestMapping = ClassName.get("org.springframework.web.bind.annotation", "RequestMapping");
        ClassName restController = ClassName.get("org.springframework.web.bind.annotation", "RestController");
        ClassName pathVariable = ClassName.get("org.springframework.web.bind.annotation", "PathVariable");
        ClassName responseBody = ClassName.get("org.springframework.web.bind.annotation", "ResponseBody");
        ClassName responseStatus = ClassName.get("org.springframework.web.bind.annotation", "ResponseStatus");
        ClassName requestParam = ClassName.get("org.springframework.web.bind.annotation", "RequestParam");
        ClassName postMapping = ClassName.get("org.springframework.web.bind.annotation", "PostMapping");
        ClassName putMapping = ClassName.get("org.springframework.web.bind.annotation", "PutMapping");
        ClassName getMapping = ClassName.get("org.springframework.web.bind.annotation", "GetMapping");
        ClassName deleteMapping = ClassName.get("org.springframework.web.bind.annotation", "DeleteMapping");
        ClassName autoWired = ClassName.get("org.springframework.beans.factory.annotation", "Autowired");
        ClassName pageable = ClassName.get("org.springframework.data.domain", "Page");
        ClassName list = ClassName.get("java.util", "List");

        TypeSpec controllerClass = TypeSpec.classBuilder(controllerClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(restController)
                .addAnnotation(AnnotationSpec.builder(requestMapping)
                        .addMember("value", "$S", "/api/v1/"+unCapitalize(sourceClass.getSimpleName()))
                        .build())
                .addField(FieldSpec.builder(sourceClassNameService, "service", Modifier.PRIVATE, Modifier.FINAL)
                        .addAnnotation(autoWired)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addAnnotation(autoWired)
                        .addParameter(sourceClassNameService, "service")
                        .addStatement("this.service = service")
                        .build())
                .addMethod(MethodSpec.methodBuilder("createEntity")
                        .addAnnotation(postMapping)
                        .returns(ParameterizedTypeName.get(responseEntity, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(sourceClassNameDTO, "entity")
                                .addAnnotation(requestBody)
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("$T.notNull(entity)", archbaseAssert)
                        .beginControlFlow("if (entity.getId() != null)")
                        .addStatement("$T<$T> entityOptional = service.getEntityById(entity.getId())", Optional.class, sourceClassName)
                        .beginControlFlow("if (entityOptional.isPresent())")
                        .addStatement("throw new $T(String.format(\"Já existe uma Entidade com o id %s. Não será possível salvá-la.\", entity.getId()))", validationException)
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("$T createdEntity = service.createEntity(entity.toDomain())", sourceClassName)
                        .addStatement("return ResponseEntity.ok($T.fromDomain(createdEntity))", sourceClassNameDTO)
                        .build())
                .addMethod(MethodSpec.methodBuilder("updateEntity")
                        .addAnnotation(AnnotationSpec.builder(putMapping)
                                .addMember("value", "$S", "/{id}")
                                .build())
                        .returns(ParameterizedTypeName.get(responseEntity, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(String.class, "id")
                                .addAnnotation(pathVariable)
                                .build())
                        .addParameter(ParameterSpec.builder(sourceClassNameDTO, "entity")
                                .addAnnotation(requestBody)
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("$T.notNull(id)", archbaseAssert)
                        .addStatement("$T.notNull(entity)", archbaseAssert)
                        .beginControlFlow("if (!id.equals(entity.getId()))")
                        .addStatement("throw new $T(String.format(\"Id informado %s não pode ser diferente do id da Entidade %s \", id, entity.getId()))", validationException)
                        .endControlFlow()
                        .addStatement("$T<$T> entityOptional = service.getEntityById(id)", Optional.class, sourceClassName)
                        .beginControlFlow("if (entityOptional.isEmpty())")
                        .addStatement("throw new $T(String.format(\"Entidade %s não encontrado. Não será possível salvá-la.\", id))", validationException)
                        .endControlFlow()
                        .addStatement("$T updatedEntity = service.updateEntity(entity.toDomain())", sourceClassName)
                        .addStatement("return ResponseEntity.ok($T.fromDomain(updatedEntity))", sourceClassNameDTO)
                        .build())
                .addMethod(MethodSpec.methodBuilder("removeEntity")
                        .addAnnotation(AnnotationSpec.builder(deleteMapping)
                                .addMember("value", "$S", "/{id}")
                                .build())
                        .returns(ParameterizedTypeName.get(responseEntity, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(String.class, "id")
                                .addAnnotation(pathVariable)
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("$T<$T> entityOptional = service.getEntityById(id)", Optional.class, sourceClassName)
                        .beginControlFlow("if (entityOptional.isEmpty())")
                        .addStatement("throw new $T(String.format(\"Entidade id %s não encontrada.\", id))", validationException)
                        .endControlFlow()
                        .addStatement("$T removedEntity = service.removeEntity(id)", sourceClassName)
                        .addStatement("return ResponseEntity.ok($T.fromDomain(removedEntity))", sourceClassNameDTO)
                        .build())
                .addMethod(MethodSpec.methodBuilder("getEntityById")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/{id}")
                                .build())
                        .returns(ParameterizedTypeName.get(responseEntity, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(String.class, "id")
                                .addAnnotation(pathVariable)
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .beginControlFlow("try")
                        .addStatement("$T<$T> entityOptional = service.getEntityById(id)", Optional.class, sourceClassName)
                        .addStatement("return entityOptional.map(entity -> ResponseEntity.ok($T.fromDomain(entity))).orElseGet(() -> ResponseEntity.notFound().build())", sourceClassNameDTO)
                        .nextControlFlow("catch (Exception e)")
                        .addStatement("return ResponseEntity.status($T.INTERNAL_SERVER_ERROR).build()", httpStatus)
                        .endControlFlow()
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/findAll")
                                .addMember("params", "{$S, $S}", "page", "size")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(responseStatus)
                                .addMember("value", "$T.OK", httpStatus)
                                .build())
                        .addAnnotation(responseBody)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(int.class, "page")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "page")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "size")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "size")
                                        .build())
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return service.findAll(page, size)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/findAll")
                                .addMember("params", "{$S, $S, $S}", "page", "size", "sort")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(responseStatus)
                                .addMember("value", "$T.OK", httpStatus)
                                .build())
                        .addAnnotation(responseBody)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(int.class, "page")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "page")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "size")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "size")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(ArrayTypeName.of(String.class), "sort")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "sort")
                                        .build())
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return service.findAll(page, size, sort)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/findAll")
                                .addMember("params", "{$S}", "ids")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(responseStatus)
                                .addMember("value", "$T.OK", httpStatus)
                                .build())
                        .addAnnotation(responseBody)
                        .returns(ParameterizedTypeName.get(list, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(TypeUtils.parameterize(List.class, String.class), "ids")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("required", "$L", true)
                                        .build())
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return service.findAll(ids)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("find")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/findWithFilter")
                                .addMember("params", "{$S, $S, $S}", "page", "size", "filter")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(responseStatus)
                                .addMember("value", "$T.OK", httpStatus)
                                .build())
                        .addAnnotation(responseBody)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(String.class, "filter")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "filter")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "page")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "page")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "size")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "size")
                                        .build())
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return service.findWithFilter(filter, page, size)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("find")
                        .addAnnotation(AnnotationSpec.builder(getMapping)
                                .addMember("value", "$S", "/findWithFilterAndSort")
                                .addMember("params", "{$S, $S, $S, $S}", "page", "size", "filter", "sort")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(responseStatus)
                                .addMember("value", "$T.OK", httpStatus)
                                .build())
                        .addAnnotation(responseBody)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(ParameterSpec.builder(String.class, "filter")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "filter")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "page")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "page")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(int.class, "size")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "size")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(ArrayTypeName.of(String.class), "sort")
                                .addAnnotation(AnnotationSpec.builder(requestParam)
                                        .addMember("value", "$S", "sort")
                                        .build())
                                .build())
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return service.findWithFilter(filter, page, size, sort)")
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(controllerOutputPackageBase + classMapping.targetPackageSuffix, controllerClass)
                .build();

        String packagePath = (controllerOutputPackageBase + classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + sourceClassName + ".java");

        if (!destinationFile.exists()) {
            javaFile.writeTo(outputDirectory);
        }
    }

    private void generateService(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String serviceClassName = sourceClass.getSimpleName() + "Service";

        ClassName sourceClassName = ClassName.get(sourceClass);
        ClassName sourceClassNameDTO = ClassName.get(dtoOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Dto");

        ClassName createOrUpdateOrRemoveEntityUseCase = ClassName.get("br.com.archbase.ddd.domain.contracts", "CreateOrUpdateOrRemoveEntityUseCase");
        ClassName findDataWithFilterQuery = ClassName.get("br.com.archbase.ddd.domain.contracts", "FindDataWithFilterQuery");
        ClassName sourceClassNameAdapter = ClassName.get(adapterOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"PersistenceAdapter");
        ClassName securityAdapter = ClassName.get("",securityAdapterClassName);
        ClassName archbaseValidationResult = ClassName.get("br.com.archbase.validation.fluentvalidator.context", "ArchbaseValidationResult");
        ClassName validationException = ClassName.get("br.com.archbase.validation.exception", "ArchbaseValidationException");
        ClassName pageable = ClassName.get("org.springframework.data.domain", "Page");
        ClassName optional = ClassName.get("java.util", "Optional");
        ClassName list = ClassName.get("java.util", "List");


        TypeSpec serviceClass = TypeSpec.classBuilder(serviceClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Component"))
                .addSuperinterface(ParameterizedTypeName.get(createOrUpdateOrRemoveEntityUseCase, sourceClassName, sourceClassName))
                .addSuperinterface(ParameterizedTypeName.get(findDataWithFilterQuery, ClassName.get(String.class), sourceClassNameDTO))
                .addField(sourceClassNameAdapter, "persistenceAdapter", Modifier.PRIVATE, Modifier.FINAL)
                .addField(securityAdapter, "securityAdapter", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(sourceClassNameAdapter, "persistenceAdapter")
                        .addParameter(securityAdapter, "securityAdapter")
                        .addStatement("this.persistenceAdapter = persistenceAdapter")
                        .addStatement("this.securityAdapter = securityAdapter")
                        .build())
                .addMethod(MethodSpec.methodBuilder("createEntity")
                        .addAnnotation(Override.class)
                        .returns(sourceClassName)
                        .addParameter(sourceClassName, "entity")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("$T validationResult = entity.validar()", archbaseValidationResult)
                        .beginControlFlow("if (!validationResult.isValid())")
                        .addStatement("throw new $T(validationResult.getErrors())", validationException)
                        .endControlFlow()
                        .addStatement("entity.criadoPor(securityAdapter.buscarUsuarioLogado())")
                        .addStatement("return persistenceAdapter.saveEntity(entity)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("updateEntity")
                        .addAnnotation(Override.class)
                        .returns(sourceClassName)
                        .addParameter(sourceClassName, "entity")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("$T validationResult = entity.validar()", archbaseValidationResult)
                        .beginControlFlow("if (!validationResult.isValid())")
                        .addStatement("throw new $T(validationResult.getErrors())", validationException)
                        .endControlFlow()
                        .addStatement("entity.alteradoPor(securityAdapter.buscarUsuarioLogado())")
                        .addStatement("return persistenceAdapter.saveEntity(entity)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("getEntityById")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(optional, sourceClassName))
                        .addParameter(String.class, "id")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.getEntityById(id)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findById")
                        .addAnnotation(Override.class)
                        .returns(sourceClassNameDTO)
                        .addParameter(String.class, "id")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findById(id)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(int.class, "page")
                        .addParameter(int.class, "size")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findAll(page, size)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(int.class, "page")
                        .addParameter(int.class, "size")
                        .addParameter(String[].class, "sort")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findAll(page, size, sort)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findAll")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(list, sourceClassNameDTO))
                        .addParameter(TypeUtils.parameterize(List.class, String.class), "ids")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findAll(ids)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findWithFilter")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(String.class, "filter")
                        .addParameter(int.class, "page")
                        .addParameter(int.class, "size")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findWithFilter(filter, page, size)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("findWithFilter")
                        .addAnnotation(Override.class)
                        .returns(ParameterizedTypeName.get(pageable, sourceClassNameDTO))
                        .addParameter(String.class, "filter")
                        .addParameter(int.class, "page")
                        .addParameter(int.class, "size")
                        .addParameter(String[].class, "sort")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return persistenceAdapter.findWithFilter(filter, page, size, sort)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("removeEntity")
                        .addAnnotation(Override.class)
                        .returns(sourceClassName)
                        .addParameter(String.class, "id")
                        .addModifiers(Modifier.PUBLIC)
                        .addCode("Optional<$T> entityOptional = persistenceAdapter.getEntityById(id);\n", sourceClassName)
                        .beginControlFlow("if (entityOptional.isEmpty())")
                        .addStatement("throw new $T(String.format(\"Entidade id %s não encontrada.\", id))", validationException)
                        .endControlFlow()
                        .addStatement("return persistenceAdapter.removeEntity(entityOptional.get())")
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(serviceOutputPackageBase + classMapping.targetPackageSuffix, serviceClass)
                .build();

        String packagePath = (serviceOutputPackageBase + classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + sourceClassName + ".java");

        if (!destinationFile.exists()) {
            javaFile.writeTo(outputDirectory);
        }
    }

    private void generateMapper(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String mapperClassName = sourceClass.getSimpleName() + "PersistenceMapper";
        ClassName sourceClassNameEntity = ClassName.get(persistenceOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Entity");
        ClassName entityPersistenceMapper = ClassName.get("br.com.archbase.ddd.domain.contracts", "EntityPersistenceMapper");
        ClassName sourceClassName = ClassName.get(sourceClass);

        TypeSpec mapperClass = TypeSpec.classBuilder(mapperClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.stereotype", "Component"))
                        .addMember("value", "$S", unCapitalize(mapperClassName) )
                        .build())
                .addSuperinterface(ParameterizedTypeName.get(entityPersistenceMapper, sourceClassName, sourceClassNameEntity))
                .addMethod(MethodSpec.methodBuilder("toEntity")
                        .addAnnotation(Override.class)
                        .returns(sourceClassNameEntity)
                        .addParameter(sourceClassName, "entity")
                        .addStatement("return $T.fromDomain(entity)", sourceClassNameEntity)
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                .addMethod(MethodSpec.methodBuilder("toDomain")
                        .addAnnotation(Override.class)
                        .returns(sourceClassName)
                        .addParameter(sourceClassNameEntity, "entity")
                        .addStatement("return entity.toDomain()")
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                .build();


        JavaFile javaFile = JavaFile.builder(mapperOutputPackageBase + classMapping.targetPackageSuffix, mapperClass)
                .build();

        String packagePath = (mapperOutputPackageBase + classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + mapperClassName + ".java");

        if (!destinationFile.exists()) {
            javaFile.writeTo(outputDirectory);
        }
    }

    private void generateRepository(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String repositoryClassName = sourceClass.getSimpleName() + "JpaRepository";
        ClassName sourceClassNameEntity = ClassName.get(persistenceOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Entity");
        ClassName archbaseCommonJpaRepository = ClassName.get("br.com.archbase.ddd.infraestructure.persistence.jpa.repository", "ArchbaseCommonJpaRepository");
        TypeSpec jpaRepositoryClass = TypeSpec.interfaceBuilder(repositoryClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Repository"))
                .addSuperinterface(ParameterizedTypeName.get(archbaseCommonJpaRepository, sourceClassNameEntity, ClassName.get(String.class), ClassName.get(Long.class)))
                .build();
        JavaFile javaFile = JavaFile.builder(repositoryOutputPackageBase + classMapping.targetPackageSuffix, jpaRepositoryClass)
                .build();

        String packagePath = (repositoryOutputPackageBase + classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + repositoryClassName + ".java");

        if (!destinationFile.exists()) {
            javaFile.writeTo(outputDirectory);
        }
    }

    private void generateAdapter(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String adapterClassName = sourceClass.getSimpleName() + "PersistenceAdapter";
        String repositoryClassName = sourceClass.getSimpleName()+"JpaRepository";
        ClassName entityPersistenceMapper = ClassName.get("br.com.archbase.ddd.domain.contracts", "EntityPersistenceMapper");
        ClassName entityPersistencePort = ClassName.get("br.com.archbase.ddd.domain.contracts", "EntityPersistencePort");
        ClassName findDataWithFilterQuery = ClassName.get("br.com.archbase.ddd.domain.contracts", "FindDataWithFilterQuery");
        ClassName sourceClassName = ClassName.get(sourceClass);
        ClassName sourceClassNameEntity = ClassName.get(persistenceOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Entity");
        ClassName sourceClassNameDTO = ClassName.get(dtoOutputPackageBase+classMapping.targetPackageSuffix, sourceClass.getSimpleName()+"Dto");
        ClassName pageClassName = ClassName.get("org.springframework.data.domain", "Page");
        ClassName pageableClassname = ClassName.get("org.springframework.data.domain", "Pageable");
        ClassName pageRequestClassname = ClassName.get("org.springframework.data.domain", "PageRequest");
        ClassName sortClassname = ClassName.get("org.springframework.data.domain", "Sort");
        ClassName sortUtilsClassname = ClassName.get("br.com.archbase.query.rsql.jpa", "SortUtils");ClassName pageImpl = ClassName.get("org.springframework.data.domain", "PageImpl");
        ClassName arrayListClassName = ClassName.get("java.util", "ArrayList");
        ClassName collectionClassName = ClassName.get("java.util", "Collection");
        ClassName listClassName = ClassName.get("java.util","List");


        FieldSpec repositoryField = FieldSpec.builder(ClassName.get(repositoryOutputPackageBase+classMapping.targetPackageSuffix,repositoryClassName), "repository", Modifier.PRIVATE)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
                .build();

        ParameterizedTypeName mapperType = ParameterizedTypeName.get(entityPersistenceMapper, sourceClassName, sourceClassNameEntity);
        FieldSpec mapperField = FieldSpec.builder(mapperType, "mapper", Modifier.PRIVATE)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
                .build();

        /**
         * saveEntity method
         */
        MethodSpec saveEntity = MethodSpec.methodBuilder("saveEntity")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(sourceClassName)
                .addParameter(sourceClassName, "entity")
                .build();
        saveEntity = saveEntity.toBuilder()
                .addStatement("$T persistenceEntity = mapper.toEntity(entity)", sourceClassNameEntity)
                .addStatement("$T savedEntity = repository.save(persistenceEntity)", sourceClassNameEntity)
                .addStatement("return mapper.toDomain(savedEntity)")
                .build();
        /**
         * removeEntity method
         */
        MethodSpec removeEntity = MethodSpec.methodBuilder("removeEntity")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(sourceClassName)
                .addParameter(sourceClassName, "entity")
                .build();
        removeEntity = removeEntity.toBuilder()
                .addStatement("repository.deleteById(entity.getId().toString())")
                .addStatement("return entity")
                .build();

        /**
         * getEntityById method
         */
        MethodSpec getEntityById = MethodSpec.methodBuilder("getEntityById")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Optional.class,sourceClass))
                .addParameter(String.class, "id")
                .build();
        getEntityById = getEntityById.toBuilder()
                .addStatement("Optional<$T> entityOptional = repository.findById(id)",sourceClassNameEntity)
                .addStatement("return entityOptional.map(mapper::toDomain)")
                .build();

        /**
         * getEntityByName method
         */
        MethodSpec getEntityByName = MethodSpec.methodBuilder("getEntityByName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Optional.class,sourceClass))
                .addParameter(String.class, "name")
                .build();
        getEntityByName = getEntityByName.toBuilder()
                .addStatement("return null")
                .build();

        /**
         * existsEntityByName method
         */
        MethodSpec existsEntityByName = MethodSpec.methodBuilder("existsEntityByName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(String.class, "name")
                .build();
        existsEntityByName = existsEntityByName.toBuilder()
                .addStatement("return false")
                .build();

        /**
         * existsEntityByName method
         */
        MethodSpec findById = MethodSpec.methodBuilder("findById")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(sourceClassNameDTO)
                .addParameter(String.class, "id")
                .build();
        findById = findById.toBuilder()
                .addStatement("Optional<$T> byId = repository.findById(id)", sourceClassNameEntity)
                .addStatement("return byId.map($T::toDto).orElse(null)",sourceClassNameEntity)
                .build();

        /**
         * findAll method
         */
        MethodSpec findAll = MethodSpec.methodBuilder("findAll")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageClassName,sourceClassNameDTO))
                .addParameter(int.class, "page")
                .addParameter(int.class, "size")
                .build();
        findAll = findAll.toBuilder()
                .addStatement("$T pageable = $T.of(page, size)", pageableClassname, pageRequestClassname)
                .addStatement("$T<$T> result = repository.findAll(pageable)",pageClassName, sourceClassNameEntity)
                .addStatement("List<$T> list = result.stream().map($T::toDto).toList()",sourceClassNameDTO, sourceClassNameEntity)
                .addStatement("return new PageEntity(list, pageable, result.getTotalElements())")
                .build();

        /**
         * findAll method
         */
        MethodSpec findAll2 = MethodSpec.methodBuilder("findAll")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageClassName,sourceClassNameDTO))
                .addParameter(int.class, "page")
                .addParameter(int.class, "size")
                .addParameter(String[].class, "sort")
                .build();
        findAll2 = findAll2.toBuilder()
                .addStatement("$T pageable = $T.of(page, size, $T.by($T.convertSortToJpa(sort)))", pageableClassname, pageRequestClassname, sortClassname, sortUtilsClassname)
                .addStatement("$T<$T> result = repository.findAll(pageable)",pageClassName, sourceClassNameEntity)
                .addStatement("List<$T> list = result.stream().map($T::toDto).toList()",sourceClassNameDTO, sourceClassNameEntity)
                .addStatement("return new PageEntity(list, pageable, result.getTotalElements())")
                .build();

        /**
         * findAll method
         */
        MethodSpec findAll3 = MethodSpec.methodBuilder("findAll")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(listClassName,sourceClassNameDTO))
                .addParameter(TypeUtils.parameterize(List.class, String.class), "ids")
                .build();
        findAll3 = findAll3.toBuilder()
                .addStatement("List<$T> result = repository.findAllById(ids)",sourceClassNameEntity)
                .addStatement("return result.stream().map($T::toDto).toList()",sourceClassNameEntity)
                .build();

        /**
         * findWithFilter method
         */
        MethodSpec findWithFilter = MethodSpec.methodBuilder("findWithFilter")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageClassName,sourceClassNameDTO))
                .addParameter(String.class, "filter")
                .addParameter(int.class, "page")
                .addParameter(int.class, "size")
                .build();
        findWithFilter = findWithFilter.toBuilder()
                .addStatement("$T pageable = $T.of(page, size)", pageableClassname, pageRequestClassname)
                .addStatement("$T<$T> result = repository.findAll(filter, pageable)",pageClassName, sourceClassNameEntity)
                .addStatement("List<$T> list = result.stream().map($T::toDto).toList()",sourceClassNameDTO, sourceClassNameEntity)
                .addStatement("return new PageEntity(list, pageable, result.getTotalElements())")
                .build();

        /**
         * findWithFilter method
         */
        MethodSpec findWithFilter2 = MethodSpec.methodBuilder("findWithFilter")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageClassName,sourceClassNameDTO))
                .addParameter(String.class, "filter")
                .addParameter(int.class, "page")
                .addParameter(int.class, "size")
                .addParameter(String[].class, "sort")
                .build();
        findWithFilter2 = findWithFilter2.toBuilder()
                .addStatement("$T pageable = $T.of(page, size, $T.by($T.convertSortToJpa(sort)))", pageableClassname, pageRequestClassname, sortClassname, sortUtilsClassname)
                .addStatement("$T<$T> result = repository.findAll(filter, pageable)",pageClassName, sourceClassNameEntity)
                .addStatement("List<$T> list = result.stream().map($T::toDto).toList()",sourceClassNameDTO, sourceClassNameEntity)
                .addStatement("return new PageEntity(list, pageable, result.getTotalElements())")
                .build();


        /*
         * Inner class PageEntity
         */
        TypeSpec pageEntity = TypeSpec.classBuilder("PageEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ParameterizedTypeName.get(pageImpl, sourceClassNameDTO))
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(ParameterizedTypeName.get(ClassName.get(List.class), sourceClassNameDTO), "content")
                        .addStatement("super(content)")
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(ParameterizedTypeName.get(ClassName.get(List.class), sourceClassNameDTO), "content")
                        .addParameter(pageableClassname, "pageable")
                        .addParameter(long.class, "total")
                        .addStatement("super(content, pageable, total)")
                        .build())
                .build();

        /*
         * Inner class ListEntity
         */
        TypeSpec listEntity = TypeSpec.classBuilder("ListEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ParameterizedTypeName.get(arrayListClassName, sourceClassNameDTO))
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(ParameterizedTypeName.get(collectionClassName, WildcardTypeName.subtypeOf(sourceClassNameDTO)), "c")
                        .addStatement("super(c)")
                        .build())
                .build();


        TypeSpec adapterClass = TypeSpec.classBuilder(adapterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.stereotype", "Component")).build())
                .addSuperinterface(ParameterizedTypeName.get(entityPersistencePort, sourceClassName, sourceClassName))
                .addSuperinterface(ParameterizedTypeName.get(findDataWithFilterQuery, ClassName.get(String.class), sourceClassNameDTO))
                .addField(repositoryField)
                .addField(mapperField)
                .addMethod(saveEntity)
                .addMethod(removeEntity)
                .addMethod(getEntityById)
                .addMethod(getEntityByName)
                .addMethod(existsEntityByName)
                .addMethod(findById)
                .addMethod(findAll)
                .addMethod(findAll2)
                .addMethod(findAll3)
                .addMethod(findWithFilter)
                .addMethod(findWithFilter2)
                .addType(pageEntity)
                .addType(listEntity)
                .build();

        JavaFile javaFile = JavaFile.builder(adapterOutputPackageBase + classMapping.targetPackageSuffix, adapterClass)
                .build();

        String packagePath = (adapterOutputPackageBase + classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + adapterClassName + ".java");

        if (!destinationFile.exists()) {
            javaFile.writeTo(outputDirectory);
        }
    }

    public static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                fields.add(field);
            }
        }
        fields.sort(new Comparator<Field>() {
            @Override
            public int compare(Field o1, Field o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return fields;
    }

    private void generateDTO(ClassMapping classMapping, Class<?> sourceClass, File outputDirectory) throws IOException {
        String dtoClassName = sourceClass.getSimpleName() + "Dto";
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(dtoClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("lombok", "Getter"))
                        .build())
                .addAnnotation(AnnotationSpec.builder(ClassName.get("lombok", "Builder"))
                        .build())
                .addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonIdentityInfo"))
                        .addMember("generator", "$T.class", ClassName.get("com.fasterxml.jackson.annotation.ObjectIdGenerators", "UUIDGenerator"))
                        .addMember("property", "$S", "@id")
                        .build());

        for (Field field : getAllFields(sourceClass)) {
            String fieldName = field.getName();
            TypeName fieldType = getFieldType(field);
            classBuilder.addField(FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE).build());
        }

        // Adicionar método fromDomain
        classBuilder.addMethod(createFromDomainMethod(sourceClass, dtoClassName));

        // Adicionar método toDomain
        classBuilder.addMethod(createToDomainMethod(sourceClass, dtoClassName));


        TypeSpec dtoClass = classBuilder.build();

        String packagePath = (dtoOutputPackageBase+ classMapping.targetPackageSuffix).replace('.', File.separatorChar);
        File destinationFile = new File(outputDirectory, packagePath + File.separator + dtoClassName + ".java");

        if (!destinationFile.exists()) {
            JavaFile javaFile = JavaFile.builder(dtoOutputPackageBase + classMapping.targetPackageSuffix, dtoClass)
                    .addStaticImport(Collectors.class, "toSet")
                    .build();
            javaFile.writeTo(outputDirectory);
        }
    }

    private MethodSpec createFromDomainMethod(Class<?> domainClass, String dtoClassName) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("fromDomain")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get("", dtoClassName))
                .addParameter(domainClass, "domain")
                .beginControlFlow("if (domain == null)")
                .addStatement("return null")
                .endControlFlow()
                .addCode("return $T.builder()",ClassName.get("", dtoClassName));

        for (Field field : getAllFields(domainClass)) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            if (fieldType.isArray() && fieldType.getComponentType().equals(byte.class)) {
                methodBuilder.addCode("\n\t.$L(domain.get$L())", fieldName, capitalize(fieldName));
            } else if (isComplexType(fieldType) && !fieldType.isEnum() && !field.getType().getSimpleName().equalsIgnoreCase("ArchbaseIdentifier")) {
                if (Collection.class.isAssignableFrom(fieldType)) {
                    // Para coleções de tipos complexos
                    Class<?> genericType = getGenericType(field);
                    methodBuilder.addCode("\n\t.$L(domain.get$L() != null ? domain.get$L().stream().map($T::fromDomain).collect(toSet()) : null)",
                            fieldName, capitalize(fieldName), capitalize(fieldName), ClassName.get(StringUtils.replace(genericType.getPackageName(),entityPackageBase,dtoOutputPackageBase),genericType.getSimpleName()+"Dto"));
                } else {
                    // Para tipos complexos únicos
                    methodBuilder.addCode("\n\t.$L(domain.get$L() != null ? $T.fromDomain(domain.get$L()) : null)",
                            fieldName, capitalize(fieldName), ClassName.get(StringUtils.replace(fieldType.getPackageName(),entityPackageBase,dtoOutputPackageBase),fieldType.getSimpleName()+"Dto"), capitalize(fieldName));
                }
            } else if (field.getType().getSimpleName().equalsIgnoreCase("ArchbaseIdentifier")) {
                methodBuilder.addCode("\n\t.$L(domain.get$L().toString())", fieldName, capitalize(fieldName));
            } else {
                // Tratamento para tipos não complexos
                methodBuilder.addCode("\n\t.$L(domain.get$L())", fieldName, capitalize(fieldName));
            }
        }

        methodBuilder.addCode("\n.build();\n");
        return methodBuilder.build();
    }

    private MethodSpec createToDomainMethod(Class<?> domainClass, String dtoClassName) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("toDomain")
                .addModifiers(Modifier.PUBLIC)
                .returns(domainClass)
                .addCode("return $T.builder()",domainClass);

        for (Field field : getAllFields(domainClass)) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();


            if (fieldType.isArray() && fieldType.getComponentType().equals(byte.class)) {
                methodBuilder.addCode("\n\t.$L(this.$L)", fieldName, fieldName);
            } else if (isComplexType(fieldType) && !fieldType.isEnum() && !field.getType().getSimpleName().equalsIgnoreCase("ArchbaseIdentifier"))  {
                if (Collection.class.isAssignableFrom(fieldType)) {
                    // Para coleções de tipos complexos
                    Class<?> genericType = getGenericType(field);
                    methodBuilder.addCode("\n\t.$L(this.$L != null ? this.$L.stream().map($L::toDomain).collect(toSet()) : new $T<>())",
                            fieldName, fieldName, fieldName, ClassName.get(StringUtils.replace(genericType.getPackageName(),entityPackageBase,dtoOutputPackageBase),genericType.getSimpleName()+"Dto"), ClassName.get(HashSet.class));
                } else {
                    // Para tipos complexos únicos
                    methodBuilder.addCode("\n\t.$L(this.$L != null ? this.$L.toDomain() : null)",
                            fieldName, fieldName, fieldName);
                }
            } else {
                // Tratamento para tipos não complexos
                methodBuilder.addCode("\n\t.$L(this.$L)", fieldName, fieldName);
            }
        }

        methodBuilder.addCode("\n.build();\n");
        return methodBuilder.build();
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String unCapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private TypeName getFieldType(Field field) {
        if (field.getType().getSimpleName().equalsIgnoreCase("ArchbaseIdentifier")){
            return TypeName.get(String.class);
        }
        Class<?> type = field.getType();

        // Verifica se é um tipo primitivo e retorna o tipo correspondente
        if (type.isPrimitive()) {
            return TypeName.get(type);
        }

        // Lógica para Enums
        if (type.isEnum()) {
            return ClassName.get(type);
        }

        // Lógica para Arrays
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (componentType.isPrimitive()) {
                // Tratamento especial para arrays de tipos primitivos
                return ArrayTypeName.of(TypeName.get(componentType));
            } else {
                TypeName componentTypeName = null;
                if (componentType.getPackageName().contains(entityPackageBase)){
                    componentTypeName = ClassName.get(StringUtils.replace(componentType.getPackageName(),entityPackageBase,dtoOutputPackageBase), componentType.getSimpleName() + "Dto");
                }
                // Tratamento para arrays de tipos não primitivos (incluindo complexos e enums)
                componentTypeName = componentType.isEnum() ? ClassName.get(componentType)
                        : ClassName.get("", componentType.getSimpleName() + "Dto");
                return ArrayTypeName.of(componentTypeName);
            }
        }

        // Lógica para Coleções
        if (Collection.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0) {
                    Class<?> genericClass = (Class<?>) typeArguments[0];
                    TypeName genericTypeName;

                    if (genericClass.isEnum()) {
                        // Se o tipo genérico é um enum, mantém o tipo original
                        genericTypeName = ClassName.get(genericClass);
                    } else if (isComplexType(genericClass)) {
                        // Para tipos complexos, acrescenta 'Dto'
                        genericTypeName = ClassName.get(StringUtils.replace(genericClass.getPackageName(),entityPackageBase,dtoOutputPackageBase), genericClass.getSimpleName() + "Dto");
                    } else {
                        // Para outros tipos, como primitivos, wrappers e String
                        genericTypeName = ClassName.get(genericClass);
                    }

                    // Retorna um ParameterizedTypeName para a coleção com o tipo genérico modificado
                    return ParameterizedTypeName.get(ClassName.get(type), genericTypeName);
                }
            }
            // Se a coleção não tiver um tipo genérico ou se não for possível determiná-lo
            return ClassName.get(type);
        }

        // Lógica para Tipos Complexos
        if (isComplexType(type)) {
            if (type.getPackageName().contains(entityPackageBase)){
                return ClassName.get(StringUtils.replace(type.getPackageName(),entityPackageBase,dtoOutputPackageBase), type.getSimpleName() + "Dto");
            }
            return ClassName.get("", type.getSimpleName() + "Dto");
        }

        // Para tipos não primitivos, wrappers e String
        return ClassName.get(type);
    }


    private boolean isComplexType(Class<?> type) {
        // Reutiliza a lógica de isComplexType para o tipo de componente de array
        // ou para tipos genéricos em coleções
        return !type.isPrimitive() && !isPrimitiveWrapperOrString(type) && !isKnownNonComplexType(type);
    }

    private Class<?> getGenericType(Field field) {
        Type genericType = field.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type typeArg = typeArguments[0];

                // Se o tipo genérico é uma classe, retorna seu nome simples
                if (typeArg instanceof Class) {
                    return ((Class<?>) typeArg);
                }
            }
        }
        // Retorna um valor padrão ou lança uma exceção se o tipo genérico não for encontrado
        // Pode ser necessário ajustar esta parte dependendo das suas necessidades específicas
        return Object.class;
    }

    private boolean isPrimitiveWrapperOrString(Class<?> type) {
        return type.equals(Boolean.class) || type.equals(Byte.class) ||
                type.equals(Character.class) || type.equals(Double.class) ||
                type.equals(Float.class) || type.equals(Integer.class) ||
                type.equals(Long.class) || type.equals(Short.class) ||
                type.equals(String.class);
    }

    private boolean isKnownNonComplexType(Class<?> type) {
        return type.equals(LocalDate.class) || type.equals(LocalDateTime.class) ||
                type.equals(Date.class) || type.equals(BigInteger.class) ||
                type.equals(BigDecimal.class) || type.equals(java.sql.Timestamp.class) ||
                type.equals(java.sql.Date.class) || type.equals(java.sql.Time.class);
    }



}
