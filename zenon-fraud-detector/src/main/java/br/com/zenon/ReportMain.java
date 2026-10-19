package br.com.zenon;

import br.com.zenon.fraud.report.TransactionReport;
import br.com.zenon.fraud.report.TransactionSummary;

import java.math.RoundingMode;

/**
 * Ponto de entrada da Tarefa 7: processa o arquivo completo do PaySim em stream e imprime
 * os totais.
 *
 * <p>É uma classe de entrada separada de {@link Main} de propósito. A {@code Main} carrega
 * transações em uma lista para analisá-las e consultá-las, o que a obriga a trabalhar sobre
 * uma fatia do arquivo. Este relatório atravessa os 493 MB inteiros sem guardar nada, e por
 * isso roda com {@code -Xmx128m} — misturar os dois na mesma classe apagaria justamente o
 * contraste que a tarefa quer mostrar.
 *
 * <p>Execução:
 *
 * <pre>{@code java -Xmx128m -cp target/classes br.com.zenon.ReportMain}</pre>
 */
public class ReportMain {

    /** CSV completo do PaySim: 6.362.620 transações, cerca de 493 MB. */
    private static final String ARQUIVO = "data/PS_20174392719_1491204439457_log.csv";

    void main() {
        TransactionSummary resumo = new TransactionReport().summarize(ARQUIVO);

        IO.println("Total de linhas: " + resumo.totalLines());
        IO.println("Total de fraudes: " + resumo.totalFrauds());
        IO.println("Valor total transacionado: " + formatarValor(resumo));
    }

    /**
     * Formata o valor total para a saída.
     *
     * <p>{@code toPlainString} porque o CSV traz 5.650 valores em notação científica
     * ({@code 1.0E7}), e o {@code toString} do {@link java.math.BigDecimal} propagaria esse
     * formato para o total. O {@code setScale(2)} é defensivo: a soma já chega com duas casas,
     * porque o maior {@code scale} entre as parcelas é 2, mas fixá-lo deixa a saída imune a
     * uma mudança no arquivo.
     */
    private String formatarValor(TransactionSummary resumo) {
        return resumo.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
