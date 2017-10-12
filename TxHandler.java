import java.util.*;
public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    private UTXOPool publicLedger;

    public TxHandler(UTXOPool utxoPool) {
        this.publicLedger = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */

    public boolean isValidTx(Transaction tx) {
        int index = 0;
        double inputVal = 0.0;
        double outputVal = 0.0;
        HashSet<UTXO> seenUtxo = new HashSet<UTXO>();

        for(Transaction.Input in : tx.getInputs()) {
            UTXO ut = new UTXO(in.prevTxHash, in.outputIndex);
            if (!this.publicLedger.contains(ut)) {
                return false;
            }

            double prevOutVal = publicLedger.getTxOutput(ut).value;
            inputVal += prevOutVal;

            if (seenUtxo.contains(ut)) {
                return false;
            }

            seenUtxo.add(ut);

            if (!Crypto.verifySignature(this.publicLedger.getTxOutput(ut).address ,tx.getRawDataToSign(index), in.signature)) {
                return false;
            }

            index++;

        }

        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0.0) {
                return false;
            }
            outputVal += out.value;
        }

        if (outputVal > inputVal) {
            return false;
        }

        return true;

    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        int txCount = 0;
        HashSet<Transaction> txs = new HashSet<Transaction>(Arrays.asList(possibleTxs));
        ArrayList<Transaction> valid = new ArrayList<Transaction>();

        do {
            txCount = txs.size();
            HashSet<Transaction> toRemove = new HashSet<Transaction>();
            for (Transaction tx : txs) {
                if(!isValidTx(tx)) {
                    continue;
                }

                valid.add(tx);
                refreshPool(tx);
                toRemove.add(tx);
            }

            for (Transaction tx : toRemove){
                txs.remove(tx);
            }

        } while (txCount != txs.size()  && txCount != 0);
        return valid.toArray(new Transaction[valid.size()]);
    }

    private void refreshPool(Transaction tx) {

        for(Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            this.publicLedger.removeUTXO(utxo);
        }

        byte[] txHash = tx.getHash();
        int index = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            UTXO utxo = new UTXO(txHash, index);
            index++;
            this.publicLedger.addUTXO(utxo,output);
        }
    }

}