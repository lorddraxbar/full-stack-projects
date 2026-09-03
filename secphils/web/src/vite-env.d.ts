/// <reference types="vite/client" />

// Make this file a MODULE (not a script) so the `declare module 'vue-router'`
// below is treated as an AUGMENTATION of the real vue-router types. In a
// script file the same block would be an ambient declaration that SHADOWS
// the package (every `import { useRouter } from 'vue-router'` then fails
// with TS2305 "no exported member").
export {}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// Known route meta keys (used by the beforeEach guard in router/index.ts and
// route declarations). Without this augmentation vue-router types meta as
// Record<PropertyKey, unknown> and method calls on it fail.
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    guest?: boolean
    requiresAuth?: boolean
    requiresAdmin?: boolean
    roles?: string[]
  }
}
