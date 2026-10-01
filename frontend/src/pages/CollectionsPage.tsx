import { Banknote, Landmark, ReceiptText, WalletCards } from 'lucide-react'
import { useState } from 'react'
import { CashTab } from '../components/collections/CashTab'
import { ReceiptsTab } from '../components/collections/ReceiptsTab'
import { RemittancesTab } from '../components/collections/RemittancesTab'
import { PageHeader } from '../components/PageHeader'
import { useTranslation } from '../i18n/I18nProvider'

type Tab = 'receipts' | 'remittances' | 'cash'

export function CollectionsPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [tab, setTab] = useState<Tab>('receipts')

  return <div className="page-stack">
    <PageHeader eyebrow={c('Tesorería', 'Treasury')} title={c('Cartera y caja', 'Collections and cash')} description={c('Recibos de cobro, remesas al banco y diario de caja.', 'Receipts, bank remittances and cash journal.')} icon={Banknote} />
    <nav className="workspace-tabs" aria-label={c('Áreas de cartera', 'Collection areas')}>
      <button type="button" className={tab === 'receipts' ? 'active' : ''} onClick={() => setTab('receipts')}><ReceiptText size={15} />{c('Recibos', 'Receipts')}</button>
      <button type="button" className={tab === 'remittances' ? 'active' : ''} onClick={() => setTab('remittances')}><Landmark size={15} />{c('Remesas', 'Remittances')}</button>
      <button type="button" className={tab === 'cash' ? 'active' : ''} onClick={() => setTab('cash')}><WalletCards size={15} />{c('Caja', 'Cash')}</button>
    </nav>
    {tab === 'receipts' && <ReceiptsTab />}
    {tab === 'remittances' && <RemittancesTab />}
    {tab === 'cash' && <CashTab />}
  </div>
}
