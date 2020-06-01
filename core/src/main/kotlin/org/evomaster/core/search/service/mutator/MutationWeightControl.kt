package org.evomaster.core.search.service.mutator

import com.google.inject.Inject
import org.evomaster.core.EMConfig
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.impact.Impact
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.geneMutation.ArchiveMutator
import kotlin.math.max

/**
 * control mutation weight which requires [apc] and [archiveMutator]
 */
class MutationWeightControl {

    @Inject
    private lateinit var randomness: Randomness

    @Inject
    private lateinit var config : EMConfig

    @Inject
    lateinit var apc : AdaptiveParameterControl

    @Inject
    lateinit var archiveMutator : ArchiveMutator


    /**
     * @return a non-empty subset of [candidateGenesToMutate] to mutate.
     */
    fun selectSubGene(
            candidateGenesToMutate: List<Gene>,
            grouped : List<List<Gene>>? = null,
            adaptiveWeight: Boolean,
            targets: Set<Int>? = null,
            impacts: List<Impact>?= null,
            individual: Individual?= null,
            evi: EvaluatedIndividual<*>?= null) : List<Gene>{

        if (grouped != null){
            if (grouped.flatten().size != candidateGenesToMutate.size || !candidateGenesToMutate.containsAll(grouped.flatten()))
                throw IllegalArgumentException("grouped and candidateGenesToMutate are mismatched")
        }

        val numToMutate = apc.getExploratoryValue(max(1.0, config.startingPerOfGenesToMutate * candidateGenesToMutate.size), 1.0)
        val mutated = mutableListOf<Gene>()

        //by default, weight of all mutable genes is 1
        val weights = candidateGenesToMutate.map { Pair(it, 1.0) }.toMap().toMutableMap()

        /*
            mutation rate can be manipulated by different weight methods
            eg, only depends on static weight, or impact derived based on archive (archive-based solution)
         */
        if(adaptiveWeight){
            if (targets == null || (individual == null && impacts == null) || evi == null)
                throw IllegalArgumentException("invalid inputs: when adaptive weight is applied, targets, evi and individual(or impacts) should not be null")
            archiveMutator.calculateWeightByArchive(candidateGenesToMutate, weights, individual = individual, impacts = impacts, evi = evi, targets = targets)
        } else{
            candidateGenesToMutate.forEach {
                weights[it] = it.mutationWeight().toDouble()
            }
        }

        while (mutated.isEmpty()){
            if (grouped == null){
                val sw = weights.values.sum()
                candidateGenesToMutate.forEach { g->
                    if (randomness.nextBoolean(calculatedAdaptiveMutationRate(candidateGenesToMutate.size, config.d, numToMutate, sw, weights.getValue(g))))
                        mutated.add(g)
                }
            }else{
                grouped.forEach { gr->
                    val sw = gr.map { weights.getValue(it) }.sum()
                    val t = numToMutate * gr.size / candidateGenesToMutate.size
                    gr.forEach {g->
                        if (randomness.nextBoolean(calculatedAdaptiveMutationRate(gr.size, config.d, t, sw, weights.getValue(g))))
                            mutated.add(g)
                    }
                }
            }
        }
        return mutated
    }

    private fun calculatedAdaptiveMutationRate(n : Int, d : Double, t: Double, sw: Double, w : Double) = t * (d/n + (1.0-d) * w/sw)

}