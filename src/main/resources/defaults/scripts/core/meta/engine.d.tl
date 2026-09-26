global type core = record
    SCRIPTS_PATH: string
    MODULES_PATH: string
    worlds: {world.WorldId: world.World}

    type id = record
        type Literal = string
        type Reference = Literal | Id

        interface Id
            namespace: string
            loc: string
        end

        new: function(namespace: string, loc: string): Id
        parse: function(value: Literal): Id, boolean
    end

    type vec3 = record
        interface Vec3
            x: number
            y: number
            z: number

            copy: function(self: Vec3): Vec3
            set: function(self: Vec3, other: Vec3): Vec3
            add: function(self: Vec3, other: Vec3): Vec3
            sub: function(self: Vec3, other: Vec3): Vec3
            mul: function(self: Vec3, scalar: number): Vec3
            div: function(self: Vec3, scalar: number): Vec3
            normalize: function(self: Vec3): Vec3
            dot: function(self: Vec3, other: Vec3): number
            length_squared: function(self: Vec3): number
            length: function(self: Vec3): number
            normalized: function(self: Vec3): Vec3
        end
        new: function(x: number, y: number, z: number): Vec3
    end

    type component = record
        interface Type<T>
            id: id.Id
        end

        get_type: function<T>(id: id.Reference): Type<T>, boolean
    end

    type player = record
        interface Player
            uuid: string
            id: integer
            entity: world.Entity
        end

        enum Mode
            "default"
            "spectator"
            "game_master"
        end

        interface PlayerComponent
            player: Player
        end

        interface ModeComponent
            mode: Mode
            is_spectator: boolean
            is_game_master: boolean
        end

        interface PhysicsComponent
            no_clip: boolean
        end

        interface InputComponent
            is_sprinting: boolean
        end

        interface AttributesComponent
            speed: number
            fly_speed: number
            jump_strength: number
            gravity: number
        end

        interface CustomAttributesComponent
            speed: number
            jump_strength: number
            gravity: number
        end

        interface MovementStatusComponent
            intention: number
            stamina: number
        end

        interface VelocityComponent
            motion: vec3.Vec3
            previous: vec3.Vec3
        end

        interface InventoryComponent
            main_hand_item: world.Entity
            off_hand_item: world.Entity
            selected_slot: integer
            items: {world.Entity}
        end

        type JumpComponent = boolean
    end

    type world = record
        interface Entity
            world: world.World
            id: integer
            readable: boolean

            exists: function(self: Entity): boolean
            get_component: function<T>(self: Entity, type: component.Type<T>): T
            has_component: function(self: Entity, type: component.Type): boolean
            set_component: function(self: Entity, type: component.Type, value: any)
            remove_component: function<T>(self: Entity, type: component.Type<T>): T
            mark_updated: function(self: Entity, type: component.Type)
            list_components: function(self: Entity): {component.Type, any}
            destroy: function(self: Entity)
        end

        type WorldId = string
        type DynamicVoxel = Entity
        type Event = Entity

        interface WriteOnlyEntity is Entity
            readable: boolean
        end

        enum DynamicVoxelError
            "chunk_not_loaded"
            "position_occupied"
            "invalid_position"
        end

        interface VoxelMeta
            id: string
            has_tag: function(tag: string): boolean
        end

        interface World
            id: WorldId
            is_client: boolean
            players: {player.Player}

            add_entity: function(self: World): world.Entity
            add_dynamic_voxel: function(self: World, position: vec3.Vec3, networked: boolean): DynamicVoxel, DynamicVoxelError
            get_dynamic_voxel: function(self: World, position: vec3.Vec3): DynamicVoxel
            emit: function(self: World, event: Event, networked: boolean): Event
            iterate: function(
                self: World,
                types: {component.Type},
                callback: function(World, world.Entity, ...: any)
            )
        end

        type LocationComponent = vec3.Vec3

        interface DynamicVoxelComponent
            pos: {number}
        end

        interface DynamicVoxelInterestComponent
        end

        enum LightBehaviourType
            "sphere"
        end

        interface LightBehaviourParams
            radius: integer
        end

        interface LightBehaviour
            type: LightBehaviourType
            params: LightBehaviourParams
        end

        interface LightSourceComponent
            behaviour: LightBehaviour
        end

        interface LuminanceComponent
            level: integer
        end

        interface DoorComponent
            open: boolean
        end

        type UseRestrictionComponent = boolean
    end

    type rpc = record
        interface EntityRpcMessage
            sender: player.Player
            data: {any}
        end

        interface ReceiverComponent
            messages: {EntityRpcMessage}
        end

        interface QueueComponent
            send: function(self: QueueComponent, value: {any})
        end
    end

    type context = record
        interface Script
        end

        interface LoadItem is Script
            item: world.Entity
            world: world.World
        end

        interface Interaction is Script
            player: player.Player
            raycast_player: player.Player
        end

        interface PlayerInputTick is Script
            player: player.Player
        end

        interface VoxelAction is Script
            player: player.Player
            world: world.World
            voxel_pos: {number}
            voxel_meta: world.VoxelMeta
        end

        interface Operation is Script
        end

        interface WebWidgetBehaviour
        end

        interface WorkspaceOpen is Script
            add_window: function(
                self: WorkspaceOpen,
                id: string,
                url: string,
                width: number,
                height: number
            ): WebWidgetBehaviour
            game_session: game_session
        end
    end

    type build = record
        interface Build
            namespaces: {Namespace}
        end

        interface Namespace
            id: string
            items: {ItemPrefab}
            scripts: {Script}
            operations: {Operation}
            components: {ComponentType}
            systems: {System}
        end

        interface System
            id: id.Reference
            query: {id.Reference}
            tick: function(world.World, world.Entity, ...: any)
        end

        interface ComponentType
            id: id.Reference
            replicating: boolean
            persistent: boolean
        end

        interface Script
            id: id.Reference
            execute: function(context.Script)
        end

        interface ItemPrefab
            id: id.Reference
            max_count: integer
            assets: {string: id.Id}
            on_load: function(world.World, world.WriteOnlyEntity)
        end

        interface Operation
            id: id.Reference
            script: string
            name: string
            inputs: {OperationInput}
            permission: boolean
        end

        interface OperationInput
            id: id.Reference
            type: OperationInputType
        end

        enum OperationInputType
            "text"
            "int"
            "double"
            "logic"
            "table"
        end
    end

    type modules = record
        enabled: {Module}

        interface ModuleEntry
            path: string
        end

        interface ModuleFile is ModuleEntry
            read: function(self): string
        end

        interface ModuleFolder is ModuleEntry
            files: function(self): {string: ModuleEntry}
        end

        interface Module
            filepath: string
            dir: ModuleFolder
            namespace: string
            namespaces: {string}
            depends: {string}
        end
    end

    type game_session = record
        main_player: player.Player
        
        type audio = record
            type SoundId = string
            type SoundReference = SoundId | Sound

            enum SoundCategory
                "master"
                "weather"
                "blocks"
                "hostile"
                "neutral"
                "players"
                "ambient"
                "voice"
            end

            interface Source
                sound: SoundId
                category: SoundCategory
                x: number
                y: number
                z: number
                looping: boolean
                spatial: boolean
                volume: number --- 0 to 1
                pitch: number --- -1 to 2
                ended: boolean
                slot: string
                radius: integer

                play: function(self: Source)
                stop: function(self: Source)
            end

            interface SourceParameters
                sound: Sound
                category: SoundCategory ---nullable
                x: number ---nullable, 0
                y: number ---nullable, 0
                z: number ---nullable, 0
                looping: boolean ---nullable, false
                spatial: boolean ---nullable, false
                volume: number ---nullable, 1
                pitch: number ---nullable, 1
                radius: integer ---nullable, 16
            end

            interface Sound
                id: SoundId
                stream: boolean
            end

            create_source: function(params: SourceParameters): Source
        end
    end

    type logger = record
        info: function(message: string, ...: string)
        warn: function(message: string, ...: string)
        error: function(message: string, ...: string)
        debug: function(message: string, ...: string)
    end
end

global engine: core

return core
