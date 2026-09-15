# O EvalEx é compilado com Lombok e carrega referências à anotação
# lombok.Generated (retenção CLASS, sem efeito em runtime). O R8 não a
# encontra no classpath e pararia o build; ignorar o aviso é o esperado.
-dontwarn lombok.Generated

# ---------------------------------------------------------------------------
# Relatório de falha (app/src/main/java/app/cascata/launcher/crash/)
#
# A release é minificada pelo R8: sem isto o rastro que a tela de relatório
# mostra sai sem arquivo e sem número de linha — só `Unknown Source`, que não
# diz onde a falha aconteceu. Estas três opções preservam a linha original em
# cada quadro do stack trace.
#
# Os NOMES de classe e método continuam ofuscados, de propósito: desofuscá-los
# é trabalho do `mapping.txt` gerado a cada release (build/outputs/mapping/
# <variante>/mapping.txt), que fica com quem publica e é o único capaz de
# traduzir um relatório de volta. Guardar o mapping de cada versão publicada é
# o que torna um relatório colado por um usuário legível meses depois — sem
# ele, o texto continua útil (versão, aparelho, tipo da exceção, linhas), mas
# os nomes não voltam.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
