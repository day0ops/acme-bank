import { useRef, useState, type RefObject } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion, useScroll, useTransform, type MotionValue } from 'framer-motion'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { sandboxDashboard } from '@/lib/sandboxData'
import { cn, formatMoney, transactionStatusLabel } from '@/lib/utils'

// design-taste-frontend direction: "product-performs" (seed 1118060246,
// sector finance, drawn 2026-10-17). The idea: Acme Bank's real
// multi-service ledger becomes a working fragment of the actual dashboard,
// shown before anyone logs in, instead of stock copy about "features". See
// web-task-3-report.md for the full direction sheet, redraws and notes.

const EASE_OUT = [0.22, 1, 0.36, 1] as const

/** Staggered rise-and-fade entrance, played once as a section scrolls into view. */
function fadeUp(reduceMotion: boolean | null, index = 0) {
  return {
    initial: { opacity: 0, y: reduceMotion ? 0 : 20 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, amount: 0.4 },
    transition: { duration: 0.6, delay: index * 0.08, ease: EASE_OUT },
  }
}

const checkingView = sandboxDashboard.accounts.find((view) => view.account.type === 'ACCOUNT_TYPE_CHECKING')!
const savingsView = sandboxDashboard.accounts.find((view) => view.account.type === 'ACCOUNT_TYPE_SAVINGS')!

const PRESS = 'transition-transform duration-150 active:scale-[0.97]'
// Shared type-scale steps so every section heading (and the one statement
// that plays the same visual role) resolves to the same computed size.
const HEADING = 'text-[clamp(1.75rem,calc(1.45rem_+_1.6vw),2.75rem)] font-semibold tracking-[-0.02em]'
const LEAD = 'text-[clamp(1.125rem,calc(1rem_+_0.5vw),1.375rem)] leading-relaxed'

export function LandingPage() {
  const reduceMotion = useReducedMotion()
  const heroRef = useRef<HTMLDivElement>(null)
  const { scrollYProgress } = useScroll({ target: heroRef, offset: ['start start', 'end start'] })
  const fragmentY = useTransform(scrollYProgress, [0, 1], [0, reduceMotion ? 0 : 64])

  return (
    <div className="-mx-6 -my-8 bg-stone-bg font-display text-ink">
      <Hero heroRef={heroRef} fragmentY={fragmentY} reduceMotion={reduceMotion} />
      <Underneath reduceMotion={reduceMotion} />
      <BuiltOn reduceMotion={reduceMotion} />
      <HowItWorks reduceMotion={reduceMotion} />
      <ActionTile reduceMotion={reduceMotion} />
      <QuietFooter />
    </div>
  )
}

function Hero({
  heroRef,
  fragmentY,
  reduceMotion,
}: {
  heroRef: RefObject<HTMLDivElement | null>
  fragmentY: MotionValue<number>
  reduceMotion: boolean | null
}) {
  return (
    <section ref={heroRef} className="px-6 pb-24 pt-20 md:pb-32 md:pt-28">
      <div className="grid gap-16 md:grid-cols-2 md:items-center md:gap-12">
        <div>
          <motion.h1
            {...fadeUp(reduceMotion, 0)}
            className="max-w-[14ch] text-[clamp(2.75rem,calc(1.9rem_+_4vw),5.25rem)] font-semibold leading-[0.95] tracking-[-0.03em]"
          >
            Banking with
            <br />
            the lights on.
          </motion.h1>
          <motion.p {...fadeUp(reduceMotion, 1)} className={cn('mt-8 max-w-[42ch] text-muted', LEAD)}>
            Every balance, card and transfer below reads from a real ledger: not a mockup, not sample copy.
          </motion.p>
          <motion.div {...fadeUp(reduceMotion, 2)} className="mt-10 flex flex-wrap items-center gap-4">
            <Link to="/login" className={cn(buttonVariants({ size: 'lg' }), 'bg-accent text-accent-ink hover:bg-accent/90', PRESS)}>
              Open an account
            </Link>
            <Link to="/login" className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'border-ink/20 bg-transparent text-ink hover:bg-stone-surface', PRESS)}>
              Log in
            </Link>
            <Link to="/sandbox" className="text-[13px] font-medium text-ink underline-offset-4 hover:underline">
              Try the live demo
            </Link>
          </motion.div>
        </div>

        <motion.div {...fadeUp(reduceMotion, 1)} style={{ y: fragmentY }}>
          <p className="mb-3 text-[13px] text-muted">Sample account shown below. No login required.</p>
          <div data-ui>
            <Card className="max-w-sm border-stone-field bg-white">
              <CardHeader className="flex flex-row items-center justify-between pb-4">
                <CardTitle className="text-base text-ink">Checking · {sandboxDashboard.customer.firstName} {sandboxDashboard.customer.lastName}</CardTitle>
                <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[13px] font-medium text-accent">Active</span>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-semibold tracking-tight text-ink">
                  {formatMoney(checkingView.account.balance.minorUnits, checkingView.account.balance.currencyCode)}
                </p>
                <ul className="mt-5 space-y-2 text-[13px] text-muted">
                  {checkingView.cards.map((card) => (
                    <li key={card.id}>Card ending {card.lastFour}</li>
                  ))}
                  {checkingView.recentTransactions.map((tx) => (
                    <li key={tx.id}>
                      {tx.fromAccountId === checkingView.account.id ? 'Transfer out' : 'Transfer in'} · {transactionStatusLabel(tx.status)}
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>
        </motion.div>
      </div>
    </section>
  )
}

const UNDERNEATH_NOTES_LEFT = [
  { title: 'Real balance', body: 'Stored in minor units and formatted to the cent only at the edge, never rounded along the way.' },
  { title: 'Two account types', body: "Checking and savings are the two the system supports today, nothing invented for this page." },
  { title: 'Frozen independently', body: 'Card status comes from a service written in Rust, deployed and scaled on its own.' },
]

const UNDERNEATH_NOTES_RIGHT = [
  { title: 'One screen, three services', body: 'This panel calls the accounts, cards and transactions services and stitches the result together.' },
  { title: 'Deliberately thin transfers', body: "The dashboard knows a transfer's status and its parties, not its amount: that only lives in the ledger's own API." },
  { title: 'Zero-network sandbox', body: 'This exact layout also runs on seeded data with no backend reachable at all.' },
]

function Note({ title, body, align }: { title: string; body: string; align: 'left' | 'right' }) {
  return (
    <div className={cn('max-w-[28ch]', align === 'left' ? 'ml-auto text-right' : 'text-left')}>
      <p className="text-[13px] font-medium text-ink">{title}</p>
      <p className="mt-1 text-base leading-relaxed text-ink">{body}</p>
    </div>
  )
}

function Underneath({ reduceMotion }: { reduceMotion: boolean | null }) {
  return (
    <section className="bg-stone-surface px-6 py-24 md:py-36">
      <div className="mx-auto max-w-6xl">
        <motion.h2 {...fadeUp(reduceMotion, 0)} className={HEADING}>
          What's underneath
        </motion.h2>

        <div className="mt-16 grid gap-10 lg:grid-cols-[1fr_minmax(320px,400px)_1fr] lg:items-start">
          <div className="hidden space-y-14 lg:block">
            {UNDERNEATH_NOTES_LEFT.map((note, i) => (
              <motion.div key={note.title} {...fadeUp(reduceMotion, i + 1)}>
                <Note {...note} align="left" />
              </motion.div>
            ))}
          </div>

          <motion.div {...fadeUp(reduceMotion, 2)} className="mx-auto w-full max-w-sm">
            <div data-ui>
              <Card className="border-stone-field bg-white">
                <CardHeader className="pb-4">
                  <CardTitle className="text-base">Checking</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-semibold tracking-tight text-ink">
                    {formatMoney(checkingView.account.balance.minorUnits, checkingView.account.balance.currencyCode)}
                  </p>
                  <ul className="mt-4 space-y-1.5 text-[13px] text-muted">
                    {checkingView.cards.map((card) => (
                      <li key={card.id}>Card ending {card.lastFour} · Active</li>
                    ))}
                    {checkingView.recentTransactions.map((tx) => (
                      <li key={tx.id}>
                        {tx.fromAccountId === checkingView.account.id ? 'To' : 'From'} an external account · {transactionStatusLabel(tx.status)}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            </div>
            <div data-ui className="mt-6 min-h-[220px]">
              <Card className="border-stone-field bg-white">
                <CardHeader className="pb-4">
                  <CardTitle className="text-base">Savings</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-2xl font-semibold tracking-tight text-ink">
                    {formatMoney(savingsView.account.balance.minorUnits, savingsView.account.balance.currencyCode)}
                  </p>
                  <p className="mt-4 text-[13px] text-muted">
                    {savingsView.cards.length} cards · {savingsView.recentTransactions.length} recent transactions
                  </p>
                </CardContent>
              </Card>
            </div>
          </motion.div>

          <div className="hidden space-y-14 lg:mt-10 lg:block">
            {UNDERNEATH_NOTES_RIGHT.map((note, i) => (
              <motion.div key={note.title} {...fadeUp(reduceMotion, i + 4)}>
                <Note {...note} align="right" />
              </motion.div>
            ))}
          </div>
        </div>

        {/* Below `lg` the panel can't sit between two note columns, so all six
            notes fall into one left-aligned grid instead of the split layout. */}
        <div className="mt-14 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:hidden">
          {[...UNDERNEATH_NOTES_LEFT, ...UNDERNEATH_NOTES_RIGHT].map((note, i) => (
            <motion.div key={note.title} {...fadeUp(reduceMotion, i)}>
              <Note title={note.title} body={note.body} align="right" />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

const BUILT_ON = ['Go', 'Java', 'Rust', 'Redpanda', 'Keycloak', 'Envoy']

function BuiltOn({ reduceMotion }: { reduceMotion: boolean | null }) {
  return (
    <section className="bg-stone-bg px-6 py-16 md:py-20">
      <div className="mx-auto flex max-w-6xl flex-col items-start gap-8 sm:flex-row sm:items-center sm:justify-between">
        <motion.p {...fadeUp(reduceMotion, 0)} className="text-[18px] text-muted">
          Built on
        </motion.p>
        <ul className="grid w-full grid-cols-2 gap-x-8 gap-y-6 sm:flex sm:w-auto sm:flex-wrap sm:justify-end sm:gap-x-10">
          {BUILT_ON.map((name, i) => (
            <motion.li key={name} {...fadeUp(reduceMotion, i + 1)} className="text-[18px] font-semibold tracking-tight text-ink">
              {name}
            </motion.li>
          ))}
        </ul>
      </div>
    </section>
  )
}

type StepKey = 'apply' | 'fund' | 'send'

const STEPS: { key: StepKey; label: string; clause: string }[] = [
  { key: 'apply', label: 'Apply', clause: 'Pick checking or savings.' },
  { key: 'fund', label: 'Fund', clause: 'Move money in from anywhere.' },
  { key: 'send', label: 'Send', clause: 'Send your first transfer.' },
]

function TransferRow({ label, amount, emphasis }: { label: string; amount: string; emphasis?: boolean }) {
  return (
    <div className="rounded-md border border-stone-field bg-white px-4 py-4">
      <div className="flex items-center justify-between">
        <span className="text-[13px] text-ink">{label}</span>
        <span className={cn('text-[13px] font-semibold', emphasis ? 'text-accent' : 'text-ink')}>{amount}</span>
      </div>
      <p className="mt-2 text-[13px] text-muted">Completed</p>
    </div>
  )
}

function StepFragment({ step }: { step: StepKey }) {
  if (step === 'apply') {
    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between rounded-md border-2 border-accent bg-accent/5 px-4 py-3">
          <span className="text-[13px] font-medium text-ink">Checking</span>
          <span className="h-4 w-4 rounded-full border-2 border-accent bg-accent" />
        </div>
        <div className="flex items-center justify-between rounded-md border border-stone-field px-4 py-3">
          <span className="text-[13px] text-muted">Savings</span>
          <span className="h-4 w-4 rounded-full border-2 border-stone-field" />
        </div>
      </div>
    )
  }
  if (step === 'fund') {
    return <TransferRow label="From an external account" amount="+$3,200.00" emphasis />
  }
  return <TransferRow label="To an external account" amount="-$56.00" />
}

function HowItWorks({ reduceMotion }: { reduceMotion: boolean | null }) {
  const [active, setActive] = useState<StepKey>('apply')
  const activeStep = STEPS.find((s) => s.key === active)!

  return (
    <section className="bg-stone-surface px-6 py-24 md:py-36">
      <div className="mx-auto max-w-6xl">
        <motion.h2 {...fadeUp(reduceMotion, 0)} className={HEADING}>
          Apply, fund, send
        </motion.h2>

        <motion.div {...fadeUp(reduceMotion, 1)} className="mt-14 grid gap-12 md:grid-cols-2 md:items-center md:gap-16">
          <div>
            <div className="flex gap-6">
              {STEPS.map((step) => (
                <button
                  key={step.key}
                  type="button"
                  aria-current={active === step.key}
                  onClick={() => setActive(step.key)}
                  className={cn(
                    'pb-2 text-[16px] transition-colors',
                    active === step.key ? 'border-b-2 border-ink font-medium text-ink' : 'border-b-2 border-transparent text-muted hover:text-ink',
                  )}
                >
                  {step.label}
                </button>
              ))}
            </div>
            <AnimatePresence mode="wait">
              <motion.p
                key={active}
                initial={{ opacity: 0, y: reduceMotion ? 0 : 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2, ease: EASE_OUT }}
                className={cn('mt-6 max-w-[20ch] leading-[1.05]', HEADING)}
              >
                {activeStep.clause}
              </motion.p>
            </AnimatePresence>
          </div>

          <div data-ui className="mx-auto flex min-h-[220px] w-full max-w-sm flex-col justify-center">
            <AnimatePresence mode="wait">
              <motion.div
                key={active}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2, ease: EASE_OUT }}
              >
                <StepFragment step={active} />
              </motion.div>
            </AnimatePresence>
          </div>
        </motion.div>
      </div>
    </section>
  )
}

function ActionTile({ reduceMotion }: { reduceMotion: boolean | null }) {
  return (
    <section className="bg-accent px-6 py-24 text-accent-ink md:py-32">
      <motion.div {...fadeUp(reduceMotion, 0)} className="mx-auto max-w-6xl">
        <h2 className={cn('max-w-[16ch]', HEADING)}>
          Try it before you commit
        </h2>
        <p className={cn('mt-4 max-w-[48ch] text-accent-ink/80', LEAD)}>
          Open the live sandbox with seeded data and no login, or open a real account when you're ready.
        </p>
        <div className="mt-8 flex flex-wrap gap-4">
          <Link to="/sandbox" className={cn(buttonVariants({ size: 'lg' }), 'bg-stone-bg text-ink hover:bg-white', PRESS)}>
            Try the live demo
          </Link>
          <Link
            to="/login"
            className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'border-accent-ink/40 bg-transparent text-accent-ink hover:bg-white/10', PRESS)}
          >
            Open an account
          </Link>
        </div>
        <div data-ui className="mt-12 flex min-h-[220px] w-full max-w-sm flex-col justify-center gap-3 rounded-md bg-stone-bg px-6 py-6">
          <span className="text-3xl font-semibold tracking-tight text-ink">0</span>
          <span className="text-[13px] leading-snug text-muted">network calls to load the sandbox dashboard you just saw above</span>
        </div>
      </motion.div>
    </section>
  )
}

// Computed once at module load rather than on every render.
const CURRENT_YEAR = new Date().getFullYear()

function QuietFooter() {
  return (
    <footer className="bg-stone-bg px-6 pb-8 pt-28 md:pt-40">
      <div className="mx-auto grid max-w-6xl grid-cols-1 gap-3 text-[13px] text-muted sm:grid-cols-[1fr_auto_1fr] sm:items-center sm:gap-0">
        <p>Acme Bank, a sandbox banking platform</p>
        <nav className="flex gap-6 sm:justify-center">
          <Link to="/login" className="text-ink hover:underline">Log in</Link>
          <Link to="/sandbox" className="text-ink hover:underline">Try the demo</Link>
        </nav>
        <p className="sm:text-right">© {CURRENT_YEAR}</p>
      </div>
    </footer>
  )
}
