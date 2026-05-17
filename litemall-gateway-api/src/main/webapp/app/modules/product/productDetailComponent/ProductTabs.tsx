import React, { useState } from 'react';

interface Tab {
  id: string;
  label: string;
  content: React.ReactNode;
}

interface ProductTabsProps {
  tabs: Tab[];
}

const ProductTabs: React.FC<ProductTabsProps> = ({ tabs }) => {
  const [activeTab, setActiveTab] = useState(tabs[0].id);

  return (
    <div className='row'>
      <div className='col-md-12'>
        <nav>
          <div className='nav nav-tabs' role='tablist'>
            {tabs.map(tab => (
              <button
                key={tab.id}
                className={`nav-link ${activeTab === tab.id ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
                role='tab'
                aria-controls={tab.id}
                aria-selected={activeTab === tab.id}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </nav>
        <div className='tab-content p-3 small'>
          {tabs.map(tab => (
            <div key={tab.id} className={`tab-pane fade ${activeTab === tab.id ? 'show active' : ''}`} role='tabpanel' aria-labelledby={`${tab.id}-tab`}>
              {tab.content}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ProductTabs;
