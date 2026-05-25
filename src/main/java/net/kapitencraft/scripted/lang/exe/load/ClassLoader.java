package net.kapitencraft.scripted.lang.exe.load;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.kapitencraft.scripted.Scripted;
import net.kapitencraft.scripted.lang.exe.Disassembler;
import net.kapitencraft.scripted.lang.exe.VarTypeManager;
import net.kapitencraft.scripted.lang.func.ScriptedCallable;
import net.kapitencraft.scripted.lang.holder.class_ref.ClassReference;
import net.kapitencraft.scripted.lang.oop.Package;
import net.kapitencraft.scripted.lang.oop.clazz.ScriptedClass;
import net.kapitencraft.scripted.lang.oop.method.builder.DataMethodContainer;
import net.minecraft.util.GsonHelper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ClassLoader {

    public static void load(File data) throws IOException {
        loadClasses(data);
        Scripted.LOGGER.info("Loading complete.");
    }

    public static void list(String classRef) {
        if ("$all".equals(classRef)) {
            VarTypeManager.listFlat();
        } else {
            ClassReference target = VarTypeManager.getClassForName(classRef);
            if (target == null) System.err.println("unable to find class for id '" + classRef + "'");
            else {
                ScriptedClass scriptedClass = target.get();
                System.out.println("==== Info ====");
                System.out.println("Name:    " + scriptedClass.name());
                System.out.println("Package: " + scriptedClass.pck());
                System.out.println("\n=== Methods ===");
                Map<String, DataMethodContainer> methods = scriptedClass.getMethods().asMap();
                methods.forEach((string, dataMethodContainer) -> {
                    for (ScriptedCallable method : dataMethodContainer.methods()) {
                        String name = string + "(" + VarTypeManager.getArgsSignature(method.argTypes()) + ")" + VarTypeManager.getClassName(method.retType().get());
                        if (method.isNative()) {
                            System.out.println("== " + name + " ==");
                            System.out.println("<Native>");
                        } else {
                            Disassembler.disassemble(method.getChunk(), name);
                        }
                        System.out.println();
                    }
                });
                System.out.println("==== Info End ====");
            }
        }
    }

    public static void loadClasses(File data) {
        PackageHolder<VMLoaderHolder> pckSkeleton = load(data, ".scrc", VMLoaderHolder::new);
        useClasses(pckSkeleton, (classes, pck) -> classes.forEach((name, vmLoaderHolder) -> loadHolderReference(pck, vmLoaderHolder)));
        generateSkeletons(pckSkeleton);
        generateClasses(pckSkeleton);
    }

    private static void loadHolderReference(Package pck, VMLoaderHolder holder) {
        pck.addClass(holder.name, holder.reference);
    }

    public static <T extends ClassLoaderHolder<T>> PackageHolder<T> load(File dataRoot, String end, BiFunction<File, String, T> constructor) {
        PackageHolder<T> root = new PackageHolder<>();
        List<Pair<File, PackageHolder<T>>> pckLoader = new ArrayList<>();
        pckLoader.add(Pair.of(dataRoot, root));
        while (!pckLoader.isEmpty()) {
            Pair<File, PackageHolder<T>> pck = pckLoader.getFirst();
            File file = pck.getFirst();
            PackageHolder<T> holder = pck.getSecond();
            File[] files = file.listFiles();
            if (files == null) {
                pckLoader.removeFirst();
                continue;
            }
            for (File file1 : files) {
                if (file1.isDirectory()) {
                    PackageHolder<T> child = new PackageHolder<>();
                    holder.packages.put(file1.getName(), child);
                    pckLoader.add(Pair.of(file1, child));
                } else {
                    String name = file1.getName().replace(end, "");
                    holder.classes.put(name, constructor.apply(file1, pck(dataRoot, file1)));
                }
            }
            pckLoader.removeFirst();
        }
        return root;
    }

    public static void generateSkeletons(PackageHolder<?> root) {
        useClasses(root, (classes, pck) -> classes.forEach((s, classLoaderHolder) -> classLoaderHolder.applySkeleton()));
    }

    public static void generateClasses(PackageHolder<VMLoaderHolder> root) {
        record Entry(VMLoaderHolder holder, String name, Package pck) {}

        List<Entry> entries = new ArrayList<>();

        useClasses(root, (classes, pck) -> classes.forEach((name, holder1) -> entries.add(new Entry(holder1, name, pck))));

        //elements are sorted so that the first elements have only native or no parent to ensure classes
        // with in-code parents loading after their parent and its methods
        Comparator<Entry> sortFunction = (o1, o2) -> {
            ScriptedClass o1Class = o1.holder.reference.get();
            ScriptedClass o2Class = o2.holder.reference.get();
            return o1Class.isChildOf(o2Class) ? 1 : o2Class.isChildOf(o1Class) ? -1 : o1.name.compareTo(o2.name);
        };

        Entry[] values = entries.toArray(new Entry[0]);
        Arrays.sort(values, sortFunction);

        for (Entry entry : values) {
            entry.pck.addNullableClass(entry.name, entry.holder.loadClass());
        }
    }

    //how should I name this...
    public static <T extends ClassLoaderHolder<T>> void useClasses(PackageHolder<T> root, BiConsumer<Map<String, T>, Package> consumer) {
        List<Pair<PackageHolder<T>, Package>> packageData = new ArrayList<>();
        packageData.add(Pair.of(root, VarTypeManager.rootPackage()));
        while (!packageData.isEmpty()) {
            Pair<PackageHolder<T>, Package> data = packageData.getFirst();
            PackageHolder<T> holder = data.getFirst();
            Package pck = data.getSecond();
            consumer.accept(holder.classes, pck);
            holder.packages.forEach((name, holder1) ->
                    packageData.add(Pair.of(holder1, pck.getOrCreatePackage(name))) //adding all packages back to the queue
            );
            packageData.removeFirst();
        }
    }

    public static <T extends ClassLoaderHolder<T>> void useHolders(PackageHolder<T> root, Consumer<T> consumer, Executor executor) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        List<Pair<PackageHolder<T>, Package>> packageData = new ArrayList<>();
        packageData.add(Pair.of(root, VarTypeManager.rootPackage()));
        AtomicInteger completed = new AtomicInteger(0);
        int total = root.size();
        while (!packageData.isEmpty()) {
            Pair<PackageHolder<T>, Package> data = packageData.getFirst();
            PackageHolder<T> holder = data.getFirst();
            Package pck = data.getSecond();
            holder.classes.forEach((n, o) ->
                    futures.add(CompletableFuture.runAsync(() -> consumer.accept(o), executor)
                            .whenComplete((v, ex) -> {
                                if (ex != null) {
                                    System.err.printf("error in thread: %s: %s\n", o.toString(), ex.getMessage());
                                    System.exit(1);
                                }
                                int done = completed.incrementAndGet();
                                printProgress(done, total);
                            }))
            );
            holder.packages.forEach((name, holder1) ->
                    packageData.add(Pair.of(holder1, pck.getOrCreatePackage(name))) //adding all packages back to the queue
            );
            packageData.removeFirst();
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        printProgress(total, total);
        System.out.println();
    }

    static void printProgress(int done, int total) {
        int width = 40; // bar width
        int progress = (int) ((done / (double) total) * width);

        String bar = "[" +
                "=".repeat(progress) +
                " ".repeat(width - progress) +
                "]";

        int percent = (int) ((done / (double) total) * 100);

        System.out.print("\r" + bar + " " + percent + "% (" + done + "/" + total + ")");
    }

    public static ClassReference loadClassReference(JsonObject object, String elementName) {
        return VarTypeManager.getClassOrError(GsonHelper.getAsString(object, elementName));
    }

    public static String[] loadInterfaces(JsonObject data) {
        return GsonHelper.getAsJsonArray(data, "interfaces").asList().stream().map(JsonElement::getAsString).toArray(String[]::new);
    }

    public static class PackageHolder<T extends ClassLoaderHolder<T>> {
        private final Map<String, PackageHolder<T>> packages = new HashMap<>();
        private final Map<String, T> classes = new HashMap<>();

        public void add(String pck, String name, T val) {
            String[] packages = pck.split("\\.");
            PackageHolder<T> holder = this.packages.get(packages[0]);
            for (int i = 1; i < packages.length; i++) {
                holder = holder.getOrCreate(packages[i]);
            }
            holder.classes.put(name, val);
        }

        public PackageHolder<T> getOrCreate(String name) {
            return packages.computeIfAbsent(name, n -> new PackageHolder<>());
        }

        public void forEach(Consumer<T> sink) {
            classes.values().forEach(sink);
            packages.values().forEach(h -> h.forEach(sink));
        }

        public int size() {
            int size = this.classes.size();
            for (PackageHolder<T> value : this.packages.values()) {
                size += value.size();
            }
            return size;
        }
    }

    public static String pck(File dataOrigin, File file) {
        String path = file.getPath().replace(dataOrigin.getPath(), "").replaceAll("\\..+", "");
        List<String> pckData = new ArrayList<>(List.of(path.split("\\\\")));
        pckData = pckData.subList(1, pckData.size()-1);
        return String.join(".", pckData);
    }
}
