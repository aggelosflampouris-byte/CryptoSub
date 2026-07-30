import { useState, useRef } from 'react'
import { useXmtp, ConversationMeta } from '../context/XmtpContext'
import { getMetadata, setMetadata } from '../services/metadataStore'

interface Props {
  conversation: ConversationMeta
  onClose: () => void
  onProfileUpdated: () => void
}

export default function ProfileDetailsModal({ conversation, onClose, onProfileUpdated }: Props) {
  const { selectConversation } = useXmtp()
  const [editing, setEditing] = useState(false)
  const meta = getMetadata(conversation.id)
  
  const [name, setName] = useState(meta.displayName || conversation.displayName)
  const [description, setDescription] = useState(meta.description || '')
  const [profilePicture, setProfilePicture] = useState(meta.profilePicture || '')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleSave = () => {
    setMetadata(conversation.id, {
      displayName: name,
      description,
      profilePicture
    })
    setEditing(false)
    onProfileUpdated()
  }

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    
    const reader = new FileReader()
    reader.onloadend = () => {
      const base64String = reader.result as string
      setProfilePicture(base64String)
    }
    reader.readAsDataURL(file)
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <h2>{conversation.isGroup ? 'Group Details' : 'Contact Details'}</h2>
        
        {!editing ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
            <div style={{ width: 80, height: 80, borderRadius: '50%', overflow: 'hidden', background: 'var(--bg-secondary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>
              {profilePicture ? (
                <img src={profilePicture} alt="Avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <span>{name[0]?.toUpperCase() ?? '?'}</span>
              )}
            </div>
            <h3 style={{ margin: 0 }}>{name}</h3>
            <div style={{ color: 'var(--text-secondary)', fontSize: 13, wordBreak: 'break-all' }}>
              {conversation.isGroup ? 'Group ID: ' : 'Address: '}{conversation.peerAddress}
            </div>
            {description && (
              <div style={{ background: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', width: '100%', textAlign: 'left', fontSize: 14 }}>
                {description}
              </div>
            )}
            <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button className="primary-btn" onClick={() => setEditing(true)}>Edit Profile</button>
              <button className="secondary-btn" style={{ color: 'var(--error)' }} onClick={() => {
                setMetadata(conversation.id, { isHidden: true })
                selectConversation(null)
                onClose()
              }}>Delete Contact</button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ alignSelf: 'center', position: 'relative' }}>
              <div style={{ width: 80, height: 80, borderRadius: '50%', overflow: 'hidden', background: 'var(--bg-secondary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>
                {profilePicture ? (
                  <img src={profilePicture} alt="Avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span>{name[0]?.toUpperCase() ?? '?'}</span>
                )}
              </div>
              <button 
                onClick={() => fileInputRef.current?.click()}
                style={{ position: 'absolute', bottom: -10, left: '50%', transform: 'translateX(-50%)', background: 'var(--primary-color)', color: 'white', border: 'none', borderRadius: '12px', padding: '4px 8px', fontSize: 12, cursor: 'pointer', whiteSpace: 'nowrap' }}
              >
                Change
              </button>
              <input type="file" accept="image/*" ref={fileInputRef} style={{ display: 'none' }} onChange={handleImageChange} />
            </div>
            
            <div className="form-group">
              <label>Name</label>
              <input 
                value={name} 
                onChange={e => setName(e.target.value)} 
                placeholder="Name" 
                autoFocus
              />
            </div>
            <div className="form-group">
              <label>Description (up to 300 chars)</label>
              <textarea 
                value={description} 
                onChange={e => setDescription(e.target.value.slice(0, 300))} 
                placeholder="Add a description..."
                rows={3}
                style={{ width: '100%', padding: '8px', borderRadius: '8px', background: 'var(--bg-secondary)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
              />
              <div style={{ textAlign: 'right', fontSize: 12, color: 'var(--text-tertiary)' }}>{description.length}/300</div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="primary-btn" style={{ flex: 1 }} onClick={handleSave}>Save</button>
              <button className="secondary-btn" style={{ flex: 1 }} onClick={() => setEditing(false)}>Cancel</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
