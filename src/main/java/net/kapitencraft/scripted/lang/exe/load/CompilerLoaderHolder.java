package net.kapitencraft.scripted.lang.exe.load;

import net.kapitencraft.scripted.lang.compiler.Compiler;
import net.kapitencraft.scripted.lang.compiler.Lexer;
import net.kapitencraft.scripted.lang.compiler.MethodLookup;
import net.kapitencraft.scripted.lang.compiler.analyser.SemanticAnalyser;
import net.kapitencraft.scripted.lang.compiler.bytecode.CacheBuilder;
import net.kapitencraft.scripted.lang.compiler.parser.HolderParser;
import net.kapitencraft.scripted.lang.compiler.parser.StmtParser;
import net.kapitencraft.scripted.lang.compiler.parser.VarTypeContainer;
import net.kapitencraft.scripted.lang.holder.baked.BakedClass;
import net.kapitencraft.scripted.lang.holder.oop.clazz.ClassConstructor;
import net.kapitencraft.scripted.lang.holder.token.Token;
import net.kapitencraft.scripted.lang.oop.clazz.CacheableClass;
import net.kapitencraft.scripted.lang.oop.clazz.ScriptedClass;
import net.kapitencraft.scripted.lang.oop.clazz.generated.CompileClass;
import net.kapitencraft.scripted.lang.oop.clazz.generated.RuntimeClass;
import net.minecraft.commands.CommandSourceStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class CompilerLoaderHolder extends ClassLoaderHolder<CompilerLoaderHolder> {
    private final String content;
    private final Compiler.ErrorStorage storage;
    private ClassConstructor holder;
    private Compiler.ClassBuilder builder;
    private CacheableClass target;
    private final VarTypeContainer varTypeContainer;

    public CompilerLoaderHolder(File file, String pck) {
        super(file, pck);
        try {
            content = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.storage = new Compiler.ErrorStorage(
                content.split("\n", Integer.MAX_VALUE), //second param required to not skip empty lines
                file.getAbsolutePath().replace(".\\", "") //remove '\.\'
        );
        this.varTypeContainer = new VarTypeContainer();
    }

    public CompilerLoaderHolder(ClassConstructor holder, Compiler.ErrorStorage storage, VarTypeContainer parser) {
        super(null, null);
        this.content = null; //not necessary with the holder already present
        this.storage = storage;
        this.holder = holder;
        this.varTypeContainer = parser;
    }

    public void parseSource() {
        if (this.holder != null) return; //only parse source if holder wasn't created
        Lexer lexer = new Lexer(content, storage);
        List<Token> tokens = lexer.scanTokens();
        String fileName = file.getName().replace(".scr", "");
        HolderParser parser = new HolderParser(storage);
        parser.apply(tokens.toArray(new Token[0]), varTypeContainer);

        ClassConstructor decl = parser.parseFile(fileName);

        if (decl == null) return;

        String path = ClassLoader.pck(Compiler.src, file);
        String pck = path.replace('\\', '.');
        String declPck = decl.pck();
        if (!Objects.equals(declPck, pck)) {
            storage.errorF(
                    tokens.getFirst(),
                    "package path '%s' does not match file path '%s'", declPck, pck);
        }

        holder = decl;
    }

    public void construct() {
        if (!checkHolderCreated()) return;
        StmtParser stmtParser = new StmtParser(this.storage);
        SemanticAnalyser analyser = new SemanticAnalyser(this.storage);

        stmtParser.pushFallback(this.holder.target());
        builder = holder.construct(stmtParser, analyser, this.varTypeContainer, this.storage);
        stmtParser.popFallback();
    }

    public void analyse() {
        builder.analyse();
    }

    public void cache() {
        try {
            Compiler.cache(
                    Compiler.cache,
                    new CacheBuilder(),
                    target.pck().replace(".", "/"),
                    target,
                    target.name()
            );
        } catch (IOException e) {
            System.err.println("Error saving class '" + target.absoluteName() + "': " + e.getMessage());
        }
    }

    public boolean checkHolderCreated() {
        return holder != null && !storage.hadError();
    }

    @Override
    public void applySkeleton() {
        if (checkHolderCreated()) this.holder.applySkeleton(storage);
    }

    public void finalizeLoad() {
        if (!checkHolderCreated()) return;

        if (builder.superclass() != null) {
            MethodLookup lookup = MethodLookup.createFromClass(builder.superclass().get(), builder.interfaces());
            lookup.checkAbstract(storage, builder.name(), builder.methods());
            if (builder instanceof BakedClass) {
                lookup.checkFinal(storage, builder.methods());
            }
        }
        target = builder.build();
        this.holder.target().setTarget((ScriptedClass) target);
    }

    public void validate() {
        if (!checkHolderCreated()) return;
        this.varTypeContainer.validate(this.storage);
        this.holder.validate(this.storage);
    }

    public void printErrors(CommandSourceStack errorSink) {
        this.storage.printAll(errorSink);
    }

    public void publish() {
        RuntimeClass runtimeClass = ((CompileClass) this.target).convert();
        this.holder.target().setTarget(runtimeClass);
    }
}
