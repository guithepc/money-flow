import { BACKUP_RETENTION_DAYS, VOICE_DAILY_LIMIT } from './legal'
import { SUPPORT_EMAIL } from './support'

// Textos dos Termos de uso e da Política de privacidade (PT e EN).
// Ponto de partida, não parecer jurídico: revisar antes de publicar.
// Ao mudar o conteúdo de forma relevante, suba TERMS_VERSION em lib/legal.ts e no backend.

export type LegalLang = 'pt' | 'en'

/** Parágrafo (string) ou lista com marcadores (string[]). */
export type LegalBlock = string | string[]

export interface LegalSection {
  title: string
  blocks: LegalBlock[]
}

export interface LegalDocument {
  title: string
  summary: string
  sections: LegalSection[]
}

export type LegalDocumentKind = 'terms' | 'privacy'

const TERMS: Record<LegalLang, LegalDocument> = {
  pt: {
    title: 'Termos de uso',
    summary: 'As regras para usar o Money Flow. Ao criar uma conta, você concorda com elas.',
    sections: [
      {
        title: 'O que é o Money Flow',
        blocks: [
          'O Money Flow é um serviço pessoal de controle financeiro. Você cadastra suas contas, saldos, categorias e transações pelo painel web ou por um bot do Telegram, por texto ou mensagem de voz, e acompanha tudo em gráficos e relatórios.',
          'O Money Flow não se conecta a bancos e não movimenta dinheiro: ele só guarda e organiza o que você informa.',
        ],
      },
      {
        title: 'Não é aconselhamento financeiro',
        blocks: [
          'O que o app mostra são registros e somas dos dados que você mesmo informou. Nada no Money Flow é recomendação financeira, de investimento, contábil, jurídica ou fiscal, e decisões tomadas com base nesses dados são de sua responsabilidade.',
          'Mensagens de voz e alguns textos são interpretados automaticamente por inteligência artificial, que pode errar o valor, a categoria ou a conta. Confira as transações registradas: você pode corrigi-las ou apagá-las no painel e no bot.',
        ],
      },
      {
        title: 'Sua conta',
        blocks: [
          [
            'Use um email válido e uma senha que você não usa em outros lugares.',
            'Você responde pelo que é feito com a sua conta e com o chat do Telegram vinculado a ela: quem tiver acesso a esse chat consegue registrar e consultar transações.',
            `Se perceber acesso indevido, redefina a senha (em “Esqueci minha senha” ou com /redefinir_senha no bot), o que encerra todas as sessões, e escreva para ${SUPPORT_EMAIL}.`,
          ],
        ],
      },
      {
        title: 'Uso aceitável',
        blocks: [
          'Use o Money Flow para organizar as suas próprias finanças. Não é permitido:',
          [
            'tentar acessar dados de outros usuários ou partes do sistema que não são suas;',
            'sobrecarregar o serviço, automatizar envios em massa ou contornar limites;',
            'enviar conteúdo ilegal ou usar o serviço para qualquer atividade ilegal.',
          ],
          `Para manter os custos sob controle, cada conta pode enviar até ${VOICE_DAILY_LIMIT} mensagens de voz por dia. Esse limite pode mudar; o bot avisa quando ele for atingido.`,
        ],
      },
      {
        title: 'Disponibilidade',
        blocks: [
          'O Money Flow é um projeto independente, oferecido no estado em que se encontra. Fazemos o possível para mantê-lo no ar e seus dados seguros, mas não garantimos funcionamento contínuo ou livre de erros: pode haver pausas para manutenção, falhas de terceiros (Telegram, OpenAI, hospedagem) e mudanças de funcionalidades.',
          'Recomendamos exportar seus dados de vez em quando em Configurações → Exportar meus dados.',
        ],
      },
      {
        title: 'Encerramento da conta',
        blocks: [
          'Você pode excluir sua conta a qualquer momento em Configurações → Excluir conta. A exclusão apaga todos os seus dados e não pode ser desfeita.',
          'Podemos suspender ou encerrar contas que violem estes termos. Se o serviço for descontinuado, avisaremos com antecedência razoável sempre que possível, para que você exporte seus dados.',
        ],
      },
      {
        title: 'Responsabilidade',
        blocks: [
          'Na medida permitida pela lei, o Money Flow não responde por prejuízos decorrentes de decisões tomadas com base nos dados do app, de interpretações automáticas que não foram conferidas ou de indisponibilidade do serviço. Nada nestes termos limita direitos que a legislação de defesa do consumidor garante a você.',
        ],
      },
      {
        title: 'Privacidade',
        blocks: [
          'Como coletamos, usamos e protegemos seus dados está descrito na Política de privacidade, que faz parte destes termos.',
        ],
      },
      {
        title: 'Mudanças nestes termos',
        blocks: [
          'Quando estes termos mudarem, publicaremos a nova versão nesta página, com nova data. Mudanças relevantes serão apresentadas no seu próximo login e o uso do painel dependerá de um novo aceite.',
        ],
      },
      {
        title: 'Contato',
        blocks: [`Dúvidas sobre estes termos: ${SUPPORT_EMAIL}.`],
      },
    ],
  },
  en: {
    title: 'Terms of use',
    summary: 'The rules for using Money Flow. By creating an account, you agree to them.',
    sections: [
      {
        title: 'What Money Flow is',
        blocks: [
          'Money Flow is a personal finance tracker. You record your accounts, balances, categories and transactions on the web dashboard or through a Telegram bot, by text or voice message, and follow everything in charts and reports.',
          'Money Flow does not connect to banks and does not move money: it only stores and organizes what you tell it.',
        ],
      },
      {
        title: 'Not financial advice',
        blocks: [
          'What the app shows are records and totals of the data you entered yourself. Nothing in Money Flow is financial, investment, accounting, legal or tax advice, and decisions based on this data are your own responsibility.',
          'Voice messages and some texts are interpreted automatically by artificial intelligence, which can get the amount, category or account wrong. Check the transactions it records: you can fix or delete them on the dashboard and in the bot.',
        ],
      },
      {
        title: 'Your account',
        blocks: [
          [
            'Use a valid email and a password you do not use anywhere else.',
            'You are responsible for what is done with your account and with the Telegram chat linked to it: anyone with access to that chat can record and look up transactions.',
            `If you notice unauthorized access, reset your password (via “Esqueci minha senha” or /reset_password in the bot), which ends every session, and write to ${SUPPORT_EMAIL}.`,
          ],
        ],
      },
      {
        title: 'Acceptable use',
        blocks: [
          'Use Money Flow to organize your own finances. You must not:',
          [
            'try to access other users’ data or parts of the system that are not yours;',
            'overload the service, automate bulk messages or work around limits;',
            'send illegal content or use the service for any illegal activity.',
          ],
          `To keep costs under control, each account can send up to ${VOICE_DAILY_LIMIT} voice messages per day. This limit may change; the bot tells you when it is reached.`,
        ],
      },
      {
        title: 'Availability',
        blocks: [
          'Money Flow is an independent project, provided as is. We do our best to keep it running and your data safe, but we do not guarantee uninterrupted or error-free operation: there may be maintenance pauses, third-party outages (Telegram, OpenAI, hosting) and feature changes.',
          'We recommend exporting your data from time to time in Settings → Export my data.',
        ],
      },
      {
        title: 'Account termination',
        blocks: [
          'You can delete your account at any time in Settings → Delete account. Deletion erases all your data and cannot be undone.',
          'We may suspend or close accounts that break these terms. If the service is discontinued, we will give reasonable notice whenever possible so you can export your data.',
        ],
      },
      {
        title: 'Liability',
        blocks: [
          'To the extent permitted by law, Money Flow is not liable for losses resulting from decisions based on the app’s data, from automatic interpretations that were not checked, or from the service being unavailable. Nothing in these terms limits rights that consumer protection law gives you.',
        ],
      },
      {
        title: 'Privacy',
        blocks: [
          'How we collect, use and protect your data is described in the Privacy policy, which is part of these terms.',
        ],
      },
      {
        title: 'Changes to these terms',
        blocks: [
          'When these terms change, we will publish the new version on this page with a new date. Relevant changes will be shown on your next login, and using the dashboard will require accepting them again.',
        ],
      },
      {
        title: 'Contact',
        blocks: [`Questions about these terms: ${SUPPORT_EMAIL}.`],
      },
    ],
  },
}

const PRIVACY: Record<LegalLang, LegalDocument> = {
  pt: {
    title: 'Política de privacidade',
    summary:
      'O que o Money Flow coleta, por quê, com quem compartilha e como você controla seus dados. Escrita com base na LGPD (Brasil) e no GDPR (União Europeia).',
    sections: [
      {
        title: 'Quem é o responsável',
        blocks: [
          `O controlador dos dados é o responsável pelo Money Flow. Para qualquer assunto de privacidade, escreva para ${SUPPORT_EMAIL}.`,
        ],
      },
      {
        title: 'Dados que coletamos',
        blocks: [
          [
            'Cadastro: nome, email e senha. A senha nunca é guardada; armazenamos apenas um hash (bcrypt).',
            'Telegram: o ID do chat vinculado à sua conta, o idioma escolhido no bot e o andamento da configuração inicial.',
            'Dados financeiros que você informa: contas (nome, moeda e saldo), categorias e transações (descrição, valor, data, categoria e conta).',
            'Mensagens de voz: usadas para registrar transações e não armazenadas (veja “Mensagens de voz”).',
            'Registro de aceite: data e versão dos termos e desta política que você aceitou.',
            'Sessões: para manter você conectado, guardamos de cada sessão apenas um hash do token de sessão, o navegador/dispositivo informado (user agent) e as datas de criação, expiração e encerramento.',
            'Uso de voz: quantas mensagens de voz você enviou em cada dia, para aplicar o limite diário.',
            'Redefinição de senha: de cada link de redefinição guardamos só um hash (o link vale 30 minutos e uma única vez) e as datas de criação, expiração e uso, além da data da última redefinição, usada para encerrar as sessões abertas antes dela. Para limitar pedidos repetidos, o email digitado no pedido (tenha ou não uma conta) e o horário de cada pedido ficam só na memória do servidor, fora do banco de dados.',
            'Dados técnicos: o número de cada atualização recebida do Telegram, sem conteúdo, guardado por 7 dias só para não registrar a mesma mensagem duas vezes; e logs do servidor (veja “Por quanto tempo guardamos”).',
            'No navegador: o token de acesso fica só na memória da página, e um cookie HttpOnly de sessão (restrito às rotas de login do Money Flow) mantém você conectado. No armazenamento local ficam só preferências de exibição (moeda escolhida e valores ocultos). Não usamos cookies de rastreamento, ferramentas de analytics nem anúncios.',
          ],
        ],
      },
      {
        title: 'Para que usamos e com qual base legal',
        blocks: [
          'Usamos seus dados só para prestar o serviço que você pediu ao criar a conta: guardar e mostrar suas finanças, interpretar suas mensagens e responder pelo bot. Base legal: execução de contrato (LGPD art. 7º, V; GDPR art. 6(1)(b)).',
          'O registro de aceite, o limite de pedidos de redefinição de senha e os logs técnicos servem para comprovar o aceite, manter o serviço seguro e corrigir erros. Base legal: legítimo interesse (LGPD art. 7º, IX; GDPR art. 6(1)(f)).',
          'Não vendemos seus dados, não os usamos para publicidade e não os compartilhamos além do descrito abaixo.',
        ],
      },
      {
        title: 'Quem trata dados por nós',
        blocks: [
          [
            'OpenAI (Estados Unidos): recebe o áudio das mensagens de voz para transcrição e o texto transcrito para interpretação. Durante a configuração da conta pelo bot, recebe também o que você escreve sobre suas contas. Para interpretar uma transação, enviamos junto os nomes e tipos das suas categorias e o ID, nome e saldo das suas contas. Vale a política de dados da API da OpenAI; pela versão vigente na data deste documento, esses dados não são usados para treinar modelos e podem ficar retidos pela OpenAI por até 30 dias para monitoramento de abuso.',
            'Telegram: entrega as mensagens entre você e o bot, inclusive os links de redefinição de senha e o aviso de senha alterada. O conteúdo dessas conversas, incluindo valores e saldos que o bot responde, passa pelos servidores do Telegram e segue a política de privacidade do Telegram.',
            'Provedor de hospedagem (servidor VPS): é onde rodam a aplicação e o banco de dados com todos os dados acima. O banco de dados não fica exposto à internet.',
          ],
          'OpenAI e Telegram podem tratar dados fora do Brasil e da União Europeia. Essas transferências acontecem porque são necessárias para prestar o serviço que você pediu.',
        ],
      },
      {
        title: 'Mensagens de voz',
        blocks: [
          'O áudio é baixado do Telegram para um arquivo temporário no servidor, enviado à OpenAI para transcrição e apagado logo em seguida, inclusive quando a transcrição falha. O Money Flow não guarda o áudio: fica salva apenas a transação que resultou dele.',
        ],
      },
      {
        title: 'Por quanto tempo guardamos',
        blocks: [
          [
            'Seus dados ficam guardados enquanto sua conta existir.',
            'Ao excluir a conta, todos os seus dados são apagados do banco de dados na hora, incluindo sessões, contagem de uso de voz e links de redefinição de senha, e você é desconectado.',
            'Uma sessão vale por 30 dias a partir do último uso; sair ou redefinir a senha a encerra antes. O registro da sessão é apagado até 7 dias depois da data de expiração.',
            'A contagem diária de mensagens de voz fica guardada enquanto a conta existir.',
            'Os registros de links de redefinição de senha (só o hash e as datas) ficam guardados enquanto a conta existir; um link ainda não usado é apagado quando você pede outro ou redefine a senha.',
            'O email e os horários dos pedidos de redefinição, guardados só na memória do servidor, somem até 2 horas depois do último pedido (ou antes, se o servidor reiniciar).',
            `As cópias de segurança (backups) diárias do banco expiram em até ${BACKUP_RETENTION_DAYS} dias. Até lá, dados já excluídos podem continuar nessas cópias, sem nenhum uso, e desaparecem quando elas expiram.`,
            'Os logs técnicos do servidor podem conter trechos de mensagens (por exemplo, o texto transcrito de um áudio). Eles servem só para diagnosticar erros, não são compartilhados e são descartados periodicamente.',
            'Os números de atualização do Telegram são apagados após 7 dias.',
          ],
        ],
      },
      {
        title: 'Seus direitos',
        blocks: [
          'Você pode, a qualquer momento:',
          [
            'Acessar e levar seus dados: Configurações → Exportar meus dados baixa tudo em um arquivo JSON.',
            'Corrigir dados: edite transações no painel ou peça correções por email.',
            'Excluir seus dados: Configurações → Excluir conta apaga tudo de uma vez.',
            'Saber com quem compartilhamos, se opor a um tratamento ou retirar o seu aceite (o que significa excluir a conta, já que o serviço depende desses dados).',
            'Reclamar à ANPD (Brasil) ou à autoridade de proteção de dados do seu país na União Europeia.',
          ],
          `Para qualquer pedido, escreva para ${SUPPORT_EMAIL}. Respondemos em até 15 dias.`,
        ],
      },
      {
        title: 'Segurança',
        blocks: [
          'Usamos HTTPS, guardamos senhas apenas como hash bcrypt, isolamos o banco de dados da internet e restringimos o acesso ao servidor. Nenhum sistema é totalmente seguro; se houver um incidente que afete seus dados, avisaremos você e as autoridades conforme a lei.',
        ],
      },
      {
        title: 'Menores de idade',
        blocks: ['O Money Flow não é destinado a menores de 18 anos.'],
      },
      {
        title: 'Mudanças nesta política',
        blocks: [
          'Publicaremos novas versões nesta página, com nova data. Mudanças relevantes serão apresentadas no seu próximo login para um novo aceite.',
        ],
      },
    ],
  },
  en: {
    title: 'Privacy policy',
    summary:
      'What Money Flow collects, why, who it shares it with and how you control your data. Written with the LGPD (Brazil) and the GDPR (European Union) in mind.',
    sections: [
      {
        title: 'Who is responsible',
        blocks: [
          `The data controller is the person responsible for Money Flow. For any privacy matter, write to ${SUPPORT_EMAIL}.`,
        ],
      },
      {
        title: 'Data we collect',
        blocks: [
          [
            'Sign-up: name, email and password. The password is never stored; we keep only a hash (bcrypt).',
            'Telegram: the ID of the chat linked to your account, the language you chose in the bot and your progress in the initial setup.',
            'Financial data you enter: accounts (name, currency and balance), categories and transactions (description, amount, date, category and account).',
            'Voice messages: used to record transactions and not stored (see “Voice messages”).',
            'Consent record: date and version of the terms and of this policy that you accepted.',
            'Sessions: to keep you signed in, for each session we store only a hash of the session token, the browser/device it reported (user agent) and its creation, expiry and end dates.',
            'Voice usage: how many voice messages you sent each day, to apply the daily limit.',
            'Password reset: for each reset link we store only a hash (the link is valid for 30 minutes and works once) and its creation, expiry and use dates, plus the date of the last reset, used to end the sessions opened before it. To limit repeated requests, the email entered in the request (whether or not it has an account) and the time of each request are kept only in the server’s memory, outside the database.',
            'Technical data: the number of each update received from Telegram, without content, kept for 7 days only so the same message is not recorded twice; and server logs (see “How long we keep data”).',
            'In your browser: the access token lives only in the page’s memory, and an HttpOnly session cookie (limited to Money Flow’s sign-in routes) keeps you signed in. Local storage holds only display preferences (chosen currency and hidden values). We use no tracking cookies, analytics tools or ads.',
          ],
        ],
      },
      {
        title: 'Why we use it and on what legal basis',
        blocks: [
          'We use your data only to provide the service you asked for when you created your account: storing and showing your finances, interpreting your messages and replying through the bot. Legal basis: performance of a contract (LGPD art. 7, V; GDPR art. 6(1)(b)).',
          'The consent record, the password reset request limit and technical logs are used to prove acceptance, keep the service secure and fix errors. Legal basis: legitimate interest (LGPD art. 7, IX; GDPR art. 6(1)(f)).',
          'We do not sell your data, do not use it for advertising and do not share it beyond what is described below.',
        ],
      },
      {
        title: 'Who processes data for us',
        blocks: [
          [
            'OpenAI (United States): receives the audio of voice messages for transcription and the transcribed text for interpretation. During account setup in the bot, it also receives what you write about your accounts. To interpret a transaction, we also send the names and types of your categories and the ID, name and balance of your accounts. OpenAI’s API data policy applies; under the version in force on the date of this document, this data is not used to train models and may be retained by OpenAI for up to 30 days for abuse monitoring.',
            'Telegram: delivers the messages between you and the bot, including password reset links and the password changed notice. The content of these conversations, including amounts and balances the bot replies with, goes through Telegram’s servers and is subject to Telegram’s privacy policy.',
            'Hosting provider (VPS server): runs the application and the database holding all the data above. The database is not exposed to the internet.',
          ],
          'OpenAI and Telegram may process data outside Brazil and the European Union. These transfers happen because they are necessary to provide the service you asked for.',
        ],
      },
      {
        title: 'Voice messages',
        blocks: [
          'The audio is downloaded from Telegram into a temporary file on the server, sent to OpenAI for transcription and deleted right after, including when transcription fails. Money Flow does not keep the audio: only the resulting transaction is saved.',
        ],
      },
      {
        title: 'How long we keep data',
        blocks: [
          [
            'Your data is kept for as long as your account exists.',
            'When you delete your account, all your data is erased from the database immediately, including sessions, voice usage counts and password reset links, and you are signed out.',
            'A session lasts 30 days from its last use; signing out or resetting your password ends it earlier. The session record is deleted up to 7 days after its expiry date.',
            'Daily voice message counts are kept for as long as the account exists.',
            'Password reset link records (only the hash and the dates) are kept for as long as the account exists; a link not used yet is deleted when you ask for another one or reset your password.',
            'The email and times of reset requests, kept only in the server’s memory, are gone up to 2 hours after the last request (or earlier, if the server restarts).',
            `Daily database backups expire within ${BACKUP_RETENTION_DAYS} days. Until then, deleted data may remain in those copies, unused, and it disappears when they expire.`,
            'Server logs may contain parts of messages (for example, the transcribed text of a voice message). They are used only to diagnose errors, are not shared and are discarded periodically.',
            'Telegram update numbers are deleted after 7 days.',
          ],
        ],
      },
      {
        title: 'Your rights',
        blocks: [
          'At any time, you can:',
          [
            'Access and take your data: Settings → Export my data downloads everything as a JSON file.',
            'Correct data: edit transactions on the dashboard or ask for corrections by email.',
            'Delete your data: Settings → Delete account erases everything at once.',
            'Know who we share data with, object to processing or withdraw your acceptance (which means deleting the account, since the service depends on this data).',
            'Complain to the ANPD (Brazil) or to the data protection authority of your country in the European Union.',
          ],
          `For any request, write to ${SUPPORT_EMAIL}. We reply within 15 days.`,
        ],
      },
      {
        title: 'Security',
        blocks: [
          'We use HTTPS, store passwords only as bcrypt hashes, keep the database off the internet and restrict access to the server. No system is completely secure; if an incident affects your data, we will notify you and the authorities as required by law.',
        ],
      },
      {
        title: 'Children',
        blocks: ['Money Flow is not intended for people under 18.'],
      },
      {
        title: 'Changes to this policy',
        blocks: [
          'We will publish new versions on this page with a new date. Relevant changes will be shown on your next login for you to accept again.',
        ],
      },
    ],
  },
}

export const LEGAL_DOCUMENTS: Record<LegalDocumentKind, Record<LegalLang, LegalDocument>> = {
  terms: TERMS,
  privacy: PRIVACY,
}
