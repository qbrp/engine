---@class Web
---@field open_screen fun(url: string, parameters: ScreenParameters, size_resolver: fun(screen_width: number, screen_height: number): number, number, number, number): WebWidget
Web = Web

---@class ScreenParameters
---@field pause boolean
---@field background boolean

---@class WebWidget
---@field url string
---@field on_ready fun(resolver: fun())
---@field on_event fun(id: string, resolver: fun(channel: string, payload: string))
---@field on_request fun(id: string, resolver: fun(channel: string, payload: string): string)
---@field on_close fun(resolver: fun())
---@field emit fun(id: string, payload: string)
---@field close fun()