🧠 Trade Mind | Atech Intelligence. 

Trade Mind é uma plataforma de análise de mercado que monitora criptoativos e ações da B3, gerando scores de oportunidade de forma automática.
Esse é meu projeto pessoal onde estou aplicando e evoluindo meus conhecimentos em Java, Spring Boot e React.

🚀 Funcionalidades

Scanner de ativos com score de oportunidade (RSI, volume e risco/retorno)
Diagnóstico individual por ativo com plano de ação
Ranking de melhores oportunidades atualizado automaticamente a cada 12h
Cobertura de criptomoedas (CoinGecko) e ações B3 (Brapi)
Interface em React com dark mode e gráfico de preços


📊 Como o score funciona.

O sistema analisa 4 indicadores para cada ativo e combina tudo em uma nota de 0 a 100:

RSI: Indica se o ativo está sobrecomprado ou sobrevendido.
Volume: Compara o volume atual com a média dos últimos dias.
Volatilidade: Mede o quanto o preço oscilou no período.
Risco/Retorno: Calcula se o potencial de ganho vale o risco

Quanto maior a nota, maior a oportunidade identificada pelo sistema.

🛠️ Tecnologias utilizadas

Java 21 + Spring Boot 3,
PostgreSQL (Aiven),
React.js + Tailwind CSS,
APIs: CoinGecko e Brapi,
Deploy: Render


⚙️ Como rodar localmente:
bash# Backend
./mvnw spring-boot:run

# Frontend
cd crypto-dashboard
npm install
npm start

Necessário configurar as variáveis de ambiente do banco e das APIs no application.properties.


📈 Próximos passos
* Scan autônomo a cada 12h na nuvem
* Migração para CoinGecko (estabilidade em produção)
* Deploy automático no Render via render.yaml
* Login e cadastro de usuários
* Planos de assinatura
* Alertas por email


⭐ Desenvolvido por Gabriel Anthony. 
