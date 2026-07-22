import { useState } from 'react'
import { useXmtp } from '../context/XmtpContext'

interface Props {
  onClose: () => void
}

export default function CreateGroupModal({ onClose }: Props) {
  const { client, conversations, selectConversation, refreshConversations } = useXmtp()
  const [name, setName] = useState('')
  const [selectedContacts, setSelectedContacts] = useState<Set<string>>(new Set())
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')

  const contacts = conversations.filter(c => !c.isGroup)

  const toggleContact = (id: string) => {
    setSelectedContacts(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleCreate = async () => {
    if (!client || selectedContacts.size === 0) return
    setCreating(true)
    setError('')

    try {
      const addresses = []
      for (const convId of selectedContacts) {
        const contact = contacts.find(c => c.id === convId)
        if (contact && contact.peerAddress && contact.peerAddress !== 'unknown') {
          addresses.push(contact.peerAddress)
        }
      }

      if (addresses.length === 0) {
        throw new Error('No valid addresses found for selected contacts.')
      }

      // Check if newGroup accepts addresses or inboxIds.
      // JS SDK newGroup takes inboxIds array.
      // But we can also just use the addresses if we have a helper, but wait, 
      // in V3 peerAddress actually holds the peerInboxId if it's available!
      // If it fails with addresses, we could use newGroupWithIdentifiers.
      
      let group: any
      if (typeof client.conversations.newGroupWithIdentifiers === 'function') {
        // Identifier type from @xmtp/wasm-bindings requires:
        //   identifier: string  (the Ethereum address)
        //   identifierKind: "Ethereum" | "Passkey"
        const identifiers = addresses.map(addr => ({
          identifier: addr,
          identifierKind: 'Ethereum' as const
        }))
        try {
          group = await client.conversations.newGroupWithIdentifiers(identifiers)
        } catch {
          group = await client.conversations.newGroup(addresses)
        }
      } else {
        group = await client.conversations.newGroup(addresses)
      }

      if (name.trim()) {
        try {
          await group.updateName(name.trim())
        } catch (e) {
          console.error("Failed to set group name", e)
        }
      }

      await refreshConversations()
      selectConversation(group.id)
      onClose()
    } catch (err: any) {
      setError(err.message || 'Failed to create group')
      setCreating(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <h2>Create Group</h2>
        
        {error && <div style={{ color: 'var(--error-color)', marginBottom: 16 }}>{error}</div>}
        
        <input
          type="text"
          placeholder="Group Name (Optional)"
          value={name}
          onChange={e => setName(e.target.value)}
          disabled={creating}
          style={{ width: '100%', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)', marginBottom: '16px' }}
        />

        <div style={{ marginBottom: '8px', fontWeight: 'bold' }}>Select Participants</div>
        
        <div style={{ maxHeight: '200px', overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', marginBottom: '16px' }}>
          {contacts.length === 0 ? (
            <div style={{ padding: '16px', textAlign: 'center', color: 'var(--text-tertiary)' }}>No contacts available</div>
          ) : (
            contacts.map(c => (
              <div 
                key={c.id} 
                onClick={() => toggleContact(c.id)}
                style={{ 
                  padding: '12px 16px', 
                  display: 'flex', 
                  alignItems: 'center', 
                  cursor: 'pointer',
                  borderBottom: '1px solid var(--border-color)',
                  background: selectedContacts.has(c.id) ? 'var(--bg-secondary)' : 'transparent'
                }}
              >
                <input 
                  type="checkbox" 
                  checked={selectedContacts.has(c.id)} 
                  readOnly 
                  style={{ marginRight: '12px' }} 
                />
                <div>
                  <div style={{ fontWeight: 'bold' }}>{c.displayName}</div>
                </div>
              </div>
            ))
          )}
        </div>
        
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <button className="btn-secondary" onClick={onClose} disabled={creating}>
            Cancel
          </button>
          <button className="btn-primary" onClick={handleCreate} disabled={creating || selectedContacts.size === 0}>
            {creating ? 'Creating...' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  )
}
