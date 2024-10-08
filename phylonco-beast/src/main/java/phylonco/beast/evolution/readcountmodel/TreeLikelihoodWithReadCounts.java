package phylonco.beast.evolution.readcountmodel;

import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import phylonco.beast.evolution.datatype.ReadCount;

import java.util.List;

public class TreeLikelihoodWithReadCounts extends TreeLikelihood {

    // input data is an alignment with the GT16 datatype

    // epsilon, allelic dropout, ... parameters
//    public Input<RealParameter> epsilonInput = new Input<>("epsilon", "sequencing error");
//    public Input<RealParameter> deltaInput = new Input<>("delta", "allelic dropout probability");

    public Input<LikelihoodReadCountModel> readCountModelInput = new Input<>("readCountModel", "read count model");

    public Input<ReadCount> readCountdataInput = new Input<>("readcount", "read count data for the beast.tree", Input.Validate.REQUIRED);


    private LikelihoodReadCountModel likelihoodReadCountModel;

    @Override
    public void initAndValidate() {
        super.initAndValidate();
        // your initialization (input checking for validity)

        // check Alignment type is the correct datatype (GT16)

        // set up likelihood core for memory allocation

    }

    @Override
    public double calculateLogP() {
        // get the values from input parameters
        traverse(treeInput.get().getRoot());

        // calculate log likelihood

        return 0.0;
    }

    /* Assumes there IS a branch rate model as opposed to traverse() */
    protected int traverse(final Node node) {

        int update = (node.isDirty() | hasDirt);

        final int nodeIndex = node.getNr();

        final double branchRate = branchRateModel.getRateForBranch(node);
        final double branchTime = node.getLength() * branchRate;

        // First update the transition probability matrix(ices) for this branch
        //if (!node.isRoot() && (update != Tree.IS_CLEAN || branchTime != m_StoredBranchLengths[nodeIndex])) {
        if (!node.isRoot() && (update != Tree.IS_CLEAN || branchTime != m_branchLengths[nodeIndex])) {
            m_branchLengths[nodeIndex] = branchTime;
            final Node parent = node.getParent();
            likelihoodCore.setNodeMatrixForUpdate(nodeIndex);
            for (int i = 0; i < m_siteModel.getCategoryCount(); i++) {
                final double jointBranchRate = m_siteModel.getRateForCategory(i, node) * branchRate;
                substitutionModel.getTransitionProbabilities(node, parent.getHeight(), node.getHeight(), jointBranchRate, probabilities);
                //System.out.println(node.getNr() + " " + Arrays.toString(m_fProbabilities));
                likelihoodCore.setNodeMatrix(nodeIndex, i, probabilities);
            }
            update |= Tree.IS_DIRTY;
        }

        // If the node is internal, update the partial likelihoods.
        if (!node.isLeaf()) {

            // Traverse down the two child nodes
            final Node child1 = node.getLeft(); //Two children
            final int update1 = traverse(child1);

            final Node child2 = node.getRight();
            final int update2 = traverse(child2);

            // If either child node was updated then update this node too
            if (update1 != Tree.IS_CLEAN || update2 != Tree.IS_CLEAN) {

                final int childNum1 = child1.getNr();
                final int childNum2 = child2.getNr();

                likelihoodCore.setNodePartialsForUpdate(nodeIndex);
                update |= (update1 | update2);
                if (update >= Tree.IS_FILTHY) {
                    likelihoodCore.setNodeStatesForUpdate(nodeIndex);
                }

                if (m_siteModel.integrateAcrossCategories()) {
                    likelihoodCore.calculatePartials(childNum1, childNum2, nodeIndex);
                } else {
                    throw new RuntimeException("Error TreeLikelihood 201: Site categories not supported");
                    //m_pLikelihoodCore->calculatePartials(childNum1, childNum2, nodeNum, siteCategories);
                }

                if (node.isRoot()) {
                    // No parent this is the root of the beast.tree -
                    // calculate the pattern likelihoods

                    final double[] proportions = m_siteModel.getCategoryProportions(node);
                    likelihoodCore.integratePartials(node.getNr(), proportions, m_fRootPartials);

                    // get constant pattern
                    List<Integer> constantPattern = getConstantPattern();

                    if (constantPattern != null) { // && !SiteModel.g_bUseOriginal) {
                        proportionInvariant = m_siteModel.getProportionInvariant();
                        // some portion of sites is invariant, so adjust root partials for this
                        for (final int i : constantPattern) {
                            m_fRootPartials[i] += proportionInvariant;
                        }
                    }

                    double[] rootFrequencies = substitutionModel.getFrequencies();
                    if (rootFrequenciesInput.get() != null) {
                        rootFrequencies = rootFrequenciesInput.get().getFreqs();
                    }
                    likelihoodCore.calculateLogLikelihoods(m_fRootPartials, rootFrequencies, patternLogLikelihoods);
                }

            }
        } else {
            // leaf node
            // call method to calculate leaf likelihood with read count model
            Alignment genotypeAlignment = dataInput.get();
            int taxonIndex = getTaxonIndex(node.getID(), genotypeAlignment); // taxon index
            int nrOfPatterns = genotypeAlignment.getPatternCount();
            for (int patternIndex = 0; patternIndex < nrOfPatterns; patternIndex++) {
                int state = genotypeAlignment.getPattern(taxonIndex, patternIndex);
                // update the leaf partials using read count model
                int[] states = genotypeAlignment.getDataType().getStatesForCode(state);
                likelihoodReadCountModel.calculateLogPLeaf(node, states);
            }
        }
        return update;
    } // traverseWithBRM

    /**
     *
     * @param taxon the taxon name as a string
     * @param data the alignment
     * @return the taxon index of the given taxon name for accessing its sequence data in the given alignment,
     *         or -1 if the taxon is not in the alignment.
     */
    protected int getTaxonIndex(String taxon, Alignment data) {
        int taxonIndex = data.getTaxonIndex(taxon);
        if (taxonIndex == -1) {
            if (taxon.startsWith("'") || taxon.startsWith("\"")) {
                taxonIndex = data.getTaxonIndex(taxon.substring(1, taxon.length() - 1));
            }
            if (taxonIndex == -1) {
                throw new RuntimeException("Could not find sequence " + taxon + " in the alignment");
            }
        }
        return taxonIndex;
    }

    @Override
    protected boolean requiresRecalculation() {

        boolean requiresRecal = super.requiresRecalculation();

        // check whether the other parameters are "dirty" or have been changed
        return true;
    }





}
