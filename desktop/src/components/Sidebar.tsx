import { useState } from 'react'
import { useXmtp } from '../context/XmtpContext'
import AddContactModal from '../components/AddContactModal'
import CreateGroupModal from '../components/CreateGroupModal'

interface Props {
  onOpenAccount: () => void
}

export default function Sidebar({ onOpenAccount }: Props) {
  const { conversations, activeConversationId, selectConversation, refreshConversations } = useXmtp()
  const [search, setSearch] = useState('')
  const [activeTab, setActiveTab] = useState<'all' | 'contacts' | 'groups'>('all')
  const [showAddContact, setShowAddContact] = useState(false)
  const [showCreateGroup, setShowCreateGroup] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const filtered = conversations.filter(c => {
    const matchesSearch = c.displayName.toLowerCase().includes(search.toLowerCase()) ||
                          c.peerAddress.toLowerCase().includes(search.toLowerCase())
    if (!matchesSearch) return false
    if (activeTab === 'contacts' && c.isGroup) return false
    if (activeTab === 'groups' && !c.isGroup) return false
    return true
  })

  const handleRefresh = async () => {
    setRefreshing(true)
    await refreshConversations()
    setRefreshing(false)
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h1>CryptoSub</h1>
        <div className="sidebar-actions">
          <button className="icon-btn" title="Refresh" onClick={handleRefresh} disabled={refreshing}>
            {refreshing ? <span className="spinner" style={{ width: 14, height: 14 }} /> : '↻'}
          </button>
          <button className="icon-btn" title="Create Group" onClick={() => setShowCreateGroup(true)}>
            👥
          </button>
          <button className="icon-btn" title="Add Contact" onClick={() => setShowAddContact(true)}>
            ✚
          </button>
          <button className="icon-btn" title="Account" onClick={onOpenAccount}>
            ⚙
          </button>
        </div>
      </div>

      <div className="sidebar-search">
        <input
          className="search-input"
          placeholder="Search conversations…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      <div className="sidebar-tabs" style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', marginBottom: '8px' }}>
        <button 
          className={`tab-btn ${activeTab === 'all' ? 'active' : ''}`} 
          onClick={() => setActiveTab('all')}
          style={{ flex: 1, padding: '8px', background: 'none', border: 'none', color: activeTab === 'all' ? 'var(--primary-color)' : 'var(--text-secondary)', fontWeight: activeTab === 'all' ? 'bold' : 'normal', cursor: 'pointer' }}
        >All</button>
        <button 
          className={`tab-btn ${activeTab === 'contacts' ? 'active' : ''}`} 
          onClick={() => setActiveTab('contacts')}
          style={{ flex: 1, padding: '8px', background: 'none', border: 'none', color: activeTab === 'contacts' ? 'var(--primary-color)' : 'var(--text-secondary)', fontWeight: activeTab === 'contacts' ? 'bold' : 'normal', cursor: 'pointer' }}
        >Contacts</button>
        <button 
          className={`tab-btn ${activeTab === 'groups' ? 'active' : ''}`} 
          onClick={() => setActiveTab('groups')}
          style={{ flex: 1, padding: '8px', background: 'none', border: 'none', color: activeTab === 'groups' ? 'var(--primary-color)' : 'var(--text-secondary)', fontWeight: activeTab === 'groups' ? 'bold' : 'normal', cursor: 'pointer' }}
        >Groups</button>
      </div>

      <div className="conversation-list">
        {filtered.length === 0 && (
          <div style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
            {conversations.length === 0
              ? 'No conversations yet.\nTap ✚ to add a contact.'
              : 'No results.'}
          </div>
        )}
        {filtered.map(conv => (
          <div
            key={conv.id}
            className={`conversation-item ${activeConversationId === conv.id ? 'active' : ''}`}
            onClick={() => selectConversation(conv.id)}
          >
            <div className="avatar">{conv.displayName[0]?.toUpperCase() ?? '?'}</div>
            <div className="conversation-info">
              <div className="conversation-name">{conv.displayName}</div>
              <div className="conversation-preview">{conv.lastMessage || 'No messages yet'}</div>
            </div>
            <div className="conversation-meta">
              {conv.lastMessageTs > 0 && (
                <span className="conversation-time">
                  {new Date(conv.lastMessageTs).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              )}
              {conv.unreadCount > 0 && (
                <span className="unread-badge">{conv.unreadCount}</span>
              )}
            </div>
          </div>
        ))}
      </div>

      {showAddContact && <AddContactModal onClose={() => setShowAddContact(false)} />}
      {showCreateGroup && <CreateGroupModal onClose={() => setShowCreateGroup(false)} />}
    </aside>
  )
}
