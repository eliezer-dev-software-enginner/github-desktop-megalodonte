---
name: megalodonte-app-reconstruction
description: Recreate an installed desktop application as a lean native Megalodonte app with a declarative JSONC interface, when the user asks for a Megalodonte reconstruction or migration.
---

# Recriar aplicativos com Megalodonte

Primeiro identifique o artefato instalado exato. Use a skill `reverse-engineer-anything` para inspeção estática do binário/pacote e observação do runtime quando disponível. Registre identificador/versão/hash, Evidence ID, recursos observados, inferências e lacunas. Não apresente uma função deduzida do código como comportamento observado.

Use `megalodonte-declarative/megalodonte-cliente` como referência de projeto e `megalodonte-script-app` como launcher. A interface pertence a `views/*.view.jsonc`: estados, árvore, bindings, validação e eventos. Scripts `ViewScript` contêm ações de domínio e serviços, sem montar controles. Consulte o contrato e schema de `megalodonte-jsonc-ui` para a interface declarativa. Todo controle concreto deve existir em `megalodonte-components`; o JSONC apenas valida, configura e vincula dados. Quando um controle faltar, crie-o e teste-o em `megalodonte-components`, publique o artefato e consuma a extensão pelo app. Evite WebView e imports JavaFX no consumidor. Extraia cores e identidade do artefato inspecionado, configure tema e ícone no launcher e teste o layout em janelas menores que a tela alvo.

Para operações bloqueantes, use `ViewScript.background` e entregue resultados pelos estados na thread de UI. Capture valores antes da tarefa; proteja seleções que podem mudar. Limite dados exibidos, virtualize listas extensas e descarte listeners junto à view. Teste rotas reais com dados temporários, especialmente persistência, seleção e falhas.

Empacote Linux com `python3 scripts/package.py build appimage --run` do `megalodonte-packaging`, usando `installDist`, `jlink` e a ferramenta AppImage configurada, com JDK e dependências compatíveis com a plataforma. Execute o AppImage gerado e confira o log de inicialização; se a sessão gráfica estiver inacessível, reporte isso precisamente. Relate escopo implementado e diferenças remanescentes em comparação com o alvo. Finalize com commits nos repositórios alterados. Publicação remota e notificação externa requerem autorização do pedido atual ou anterior; se autorizadas, confirme o resultado, sem repetir envios incertos.

Leia [referências locais](references.md) para caminhos e comandos neste workspace. Use o perfil do projeto em `docs/reconstruction.profile.jsonc` como modelo de parâmetros, ajustando artefato, funcionalidades, runtime e destino para cada novo aplicativo.
