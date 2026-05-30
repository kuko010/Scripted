package net.kapitencraft.scripted.lang.oop.method;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.kapitencraft.scripted.lang.compiler.Modifiers;
import net.kapitencraft.scripted.lang.exe.VarTypeManager;
import net.kapitencraft.scripted.lang.func.ScriptedCallable;
import net.kapitencraft.scripted.lang.holder.class_ref.ClassReference;
import net.kapitencraft.scripted.lang.holder.class_ref.SourceReference;
import net.kapitencraft.scripted.lang.holder.oop.attribute.ConstructorHolder;
import net.kapitencraft.scripted.lang.holder.oop.attribute.MethodHolder;
import net.kapitencraft.scripted.lang.oop.method.builder.DataMethodContainer;
import net.kapitencraft.scripted.lang.tool.StringReader;
import net.minecraft.util.GsonHelper;

import java.util.List;

public class SkeletonMethod implements ScriptedCallable {
    private final ClassReference[] args;
    private final ClassReference retType;
    private final short modifiers;

    public SkeletonMethod(ClassReference[] args, ClassReference retType, short modifiers) {
        this.args = args;
        this.retType = retType;
        this.modifiers = modifiers;
    }

    public static SkeletonMethod create(MethodHolder decl) {
        return create(decl.params(), decl.type().getReference(), decl.modifiers());
    }

    private static SkeletonMethod create(List<? extends Pair<SourceReference, String>> params, ClassReference type, short modifiers) {
        return new SkeletonMethod(params.stream().map(Pair::getFirst).map(SourceReference::getReference).toArray(ClassReference[]::new), type, modifiers);
    }

    public static SkeletonMethod create(ConstructorHolder decl, ClassReference type) {
        return create(decl.params(), type, (short) 0);
    }

    public static SkeletonMethod createNative(ClassReference[] args, ClassReference retType, short modifiers) {
        return new SkeletonMethod(args, retType, modifiers);
    }


    public static SkeletonMethod fromJson(JsonObject object) {
        ClassReference retType = VarTypeManager.parseType(new StringReader(GsonHelper.getAsString(object, "retType")));
        ClassReference[] args = GsonHelper.getAsJsonArray(object, "params").asList().stream()
                .map(JsonElement::getAsString).map(StringReader::new).map(VarTypeManager::parseType)
                .toArray(ClassReference[]::new);

        short modifiers = object.has("modifiers") ? GsonHelper.getAsShort(object, "modifiers") : 0;
        return new SkeletonMethod(args, retType, modifiers);
    }

    public static ImmutableMap<String, DataMethodContainer> readFromCache(JsonObject data, String subElementName) {
        ImmutableMap.Builder<String, DataMethodContainer> methods = new ImmutableMap.Builder<>();
        JsonObject methodData = GsonHelper.getAsJsonObject(data, subElementName);
        methodData.asMap().forEach((s, element) -> {
            SkeletonMethod[] methodDeclarations =
                    element.getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject).map(SkeletonMethod::fromJson)
                            .toArray(SkeletonMethod[]::new);
            methods.put(s, new DataMethodContainer(methodDeclarations));
        });
        return methods.build();
    }

    @Override
    public ClassReference retType() {
        return retType;
    }

    @Override
    public ClassReference[] argTypes() {
        return args;
    }

    @Override
    public Object call(Object[] arguments) {
        throw new IllegalAccessError("can not call skeleton method");
    }

    @Override
    public boolean isAbstract() {
        return Modifiers.isAbstract(modifiers);
    }

    @Override
    public boolean isFinal() {
        return Modifiers.isFinal(modifiers);
    }

    @Override
    public boolean isStatic() {
        return Modifiers.isStatic(modifiers);
    }
}
