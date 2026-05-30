package net.kapitencraft.scripted.lang.holder.oop.clazz;

import net.kapitencraft.scripted.lang.compiler.Compiler;
import net.kapitencraft.scripted.lang.compiler.Modifiers;
import net.kapitencraft.scripted.lang.compiler.analyser.SemanticAnalyser;
import net.kapitencraft.scripted.lang.compiler.parser.StmtParser;
import net.kapitencraft.scripted.lang.compiler.parser.VarTypeContainer;
import net.kapitencraft.scripted.lang.holder.ast.Expr;
import net.kapitencraft.scripted.lang.holder.ast.Stmt;
import net.kapitencraft.scripted.lang.holder.bytecode.annotation.Annotation;
import net.kapitencraft.scripted.lang.holder.class_ref.ClassReference;
import net.kapitencraft.scripted.lang.holder.oop.AnnotationObj;
import net.kapitencraft.scripted.lang.holder.oop.Validatable;
import net.kapitencraft.scripted.lang.holder.oop.attribute.FieldHolder;
import net.kapitencraft.scripted.lang.holder.token.Token;
import net.kapitencraft.scripted.lang.oop.clazz.ScriptedClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public interface ClassConstructor extends Validatable {

    AnnotationObj[] annotations();

    ClassReference target();

    Compiler.ClassBuilder construct(StmtParser stmtParser, SemanticAnalyser analyser, VarTypeContainer parser, Compiler.ErrorStorage logger);

    ScriptedClass createSkeleton(Compiler.ErrorStorage logger);

    default void applySkeleton(Compiler.ErrorStorage logger) {
        ScriptedClass skeleton = createSkeleton(logger);
        this.target().setTarget(skeleton);
    }

    Token name();

    String pck();

    default @NotNull Expr getFieldBody(StmtParser stmtParser, VarTypeContainer parser, Compiler.ErrorStorage logger, FieldHolder fieldHolder, List<Stmt> statics) {
        stmtParser.apply(fieldHolder.body(), parser);
        Expr initializer = stmtParser.expression();
        if (Modifiers.isStatic(fieldHolder.modifiers())) {
            Stmt.Expression stmt1 = new Stmt.Expression();
            {
                Expr.StaticSet staticSet = new Expr.StaticSet();
                staticSet.target = target();
                staticSet.name = fieldHolder.name();
                staticSet.value = initializer;
                staticSet.assignType = fieldHolder.assign();
                staticSet.executor = target();
                stmt1.expression = staticSet;
            }
            statics.add(stmt1);
        }
        return initializer;
    }

    default void checkFinalsPopulated(Stmt[] body, List<String> finalFields) {

    }

    default Annotation[] parseAnnotations(StmtParser stmtParser, VarTypeContainer parser) {
        List<Annotation> annotations = new ArrayList<>();
        for (AnnotationObj obj : this.annotations()) {
            annotations.add(stmtParser.parseAnnotation(obj, parser));
        }
        return annotations.toArray(Annotation[]::new);
    }
}
