package net.kapitencraft.scripted.lang.compiler.bytecode.optimize;

public interface SimpleOptimization {

    void tryExecute(BytecodeOptimizer.OptimizationStorage optimizationStorage, int index);
}
