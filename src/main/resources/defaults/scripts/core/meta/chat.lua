---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Chat
---@field messages ChatMessage[]
---@field get_message fun(id: MessageId): ChatMessage?
---@field remove_message fun(id: MessageId): ChatMessage?
---@field add_message fun(message: ChatMessage)

---@alias ChatPipelineId string
---@alias ChatChannelId string
---@alias MessageId string

---@class ChatChannel
---@field id ChatChannelId
---@field name string

---@class ChatMessage
---@field id MessageId
---@field text string
---@field alternate_text string
---@field channel ChatChannelId
---@field author PlayerId?
---@field spied boolean
---@field head SkinTextureReference?
---@field background number[]?
---@field repeat number
---@field state ComponentState

--- VoiceBlips
--- Отвечают за генерацию звуковых сигналов для голосового чата. Включают в себя параметры голоса, набор слогов и узлов, которые формируют звуковой сигнал.
--- На основе данных слогов работает эффект печатания текста в чате

---@class VoiceProperties
---@field speed number 0.6
---@field pitch number 1.7
---@field randomness number 0.7
---@field preset string "c3"
---@field frequency number 0.9
---@field volume number

---@class Syllable
---@field chars string
---@field index integer
---@field time number
---@field shake number
---@field volume number

---@class VoiceBlip
---@field syllables Syllable[]
---@field start_idx integer
---@field properties VoiceProperties
---@field entity Entity обязательно наличие LocationComponent
---@field play fun(self: VoiceBlip)
---@field stop fun (self: VoiceBlip)

--- Индикатор набора сообщений

---@class TypingIndicatorEntryResolving : ScriptContext
---@field channel ChatChannel
---@field player_id string
---@field player Player?

---@class TypingIndicatorEntry
---@field text string
---@field skin SkinTextureReference

---@class SkinTextureReference

--- Вызывается при получении сигнала о том, что игрок начал набирать сообщение в чате.
--- Возвращаемое значение содержит данные индикатора - отображаемое имя и ссылка на текстуру скина
---@alias TypingIndicatorEntryResolveEvent fun(context: TypingIndicatorEntryResolving): TypingIndicatorEntry?


--- Чат-пайплайны

---@class ChatPipeline
---@field id ChatPipelineId
---@field name string
---@field color number[]

---@class ChatInputContext : ScriptContext
---@field text string
---@field pipeline ChatPipeline

--- Вызывается, когда игрок отправляет сообщение в чат
---@alias ChatInputEvent fun(context: ChatInputContext)

--- Чат-бар

---@class ChatBar
---@field sections ChatBarSection[]
---@field mention fun(channel: ChatChannelId): boolean

---@class ChatBarSection
---@field name string
---@field channels ChatChannelId[]