package net.kapitencraft.scripted.lang.compiler.bytecode.optimize;

import net.kapitencraft.scripted.lang.compiler.bytecode.instruction.Instruction;
import net.kapitencraft.scripted.lang.compiler.bytecode.instruction.JumpInstruction;
import net.kapitencraft.scripted.lang.compiler.bytecode.optimize.impl.*;

import java.util.Iterator;
import java.util.List;

public class BytecodeOptimizer {
    public static final BytecodeOptimizer INSTANCE = new BytecodeOptimizer();

    private final List<SimpleOptimization> simpleOptimizations = List.of(
            //TODO add
            new MergeLocalInvokeOptimization(),
            new JumpMergeOptimization(), //merge jumps pointing to jumps
            new JumpIfFalseAfterFalseOptimization(), //merge jump_if_false directly after false
            new JumpReturnMergeOptimization() //replace jump with return if jump points at return
    );

    //TODO check if vars are used and if they are used before an if, otherwise move it afterwards

    //a = 0; ------------
    //if (b.isEmpty()) { |
    //  return 0;        |
    //}                  |
    //                  <
    //a ...

    private final List<AdvancedOptimization> advancedOptimizations = List.of(
            //backtrack unused pure instructions before POP or POP2
            new RemoveUnreachableOpcodesOptimization(), //remove unreachable opcodes caused by other optimizations
            new RemoveIgnoredResultInstructionsOptimization()
    );

    public void optimize(List<Instruction> instructions) {
        OptimizationStorage optimizationStorage = new OptimizationStorage(instructions);
        for (SimpleOptimization optimization : simpleOptimizations) {
            for (int i = 0; i < instructions.size(); i++) {
                optimization.tryExecute(optimizationStorage, i);
            }
        }
        for (AdvancedOptimization optimization : advancedOptimizations) {
            optimization.optimize(optimizationStorage);
        }
    }

    public static class OptimizationStorage implements Iterable<Instruction> {
        private final List<Instruction> instructions;

        private OptimizationStorage(List<Instruction> instructions) {
            this.instructions = instructions;
        }

        public Instruction getInstruction(int index) {
            return this.instructions.get(index);
        }

        public void replaceInstruction(int index, Instruction instruction) {
            this.instructions.set(index, instruction);
        }

        public void removeInstruction(int index) {
            for (Instruction instruction : this.instructions) {
                if (instruction instanceof JumpInstruction jI) {
                    if (jI.getTarget() > index) {
                        jI.setTarget(jI.getTarget() - 1);
                    }
                }
            }
            this.instructions.remove(index);
        }

        public int size() {
            return this.instructions.size();
        }

        @Override
        public Iterator<Instruction> iterator() {
            return instructions.iterator();
        }
    }
}
