


# Info for schema migration and update with flyway migration library 
What was applied

Configuration changes

- pom.xml — added flyway-core + flyway-mysql dependencies, and a second resource block that copies db/**/*.sql to the classpath without Maven filtering (critical — Maven      
  filtering would corrupt SQL with ${} substitution)
- application-db.yml — added full Flyway config block pointing at the same MySQL coordinates as Druid

Migration files (db/migration/)

┌─────────────────────────────────┬──────────────────────────────────────────────────────────┐                                                                                 
│              File               │                       What it does                       │
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V1__baseline_schema.sql         │ Current 37-table schema — only runs on a fresh database  │                                                                               
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ V2__enrich_existing_tables.sql  │ Non-breaking ALTER TABLE ADD COLUMN on 8 existing tables │                                                                                 
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V3__add_user_wallet_finance.sql │ litemall_user_bill, _recharge, _extract                  │                                                                                 
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V4__add_user_engagement.sql     │ litemall_user_integral_record, _sign, _experience_record │                                                                               
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V5__add_user_level.sql          │ litemall_system_user_level, litemall_user_level          │                                                                               
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V6__add_user_crm.sql            │ litemall_user_group, _tag, _token                        │                                                                               
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V7__add_distribution.sql        │ litemall_user_brokerage_record                           │                                                                               
├─────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                 
│ V8__add_shipping_templates.sql  │ litemall_shipping_templates + _free + _region            │                                                                               
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V9__add_flash_sales.sql          │ litemall_seckill_time, litemall_seckill                  │                                                                              
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V10__add_bargain.sql             │ litemall_bargain, _user, _help                           │                                                                              
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V11__add_order_lifecycle.sql     │ litemall_order_status                                    │                                                                              
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V12__add_system_enhancements.sql │ litemall_system_group, _group_data, _system_menu         │                                                                              
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V13__add_sms.sql                 │ litemall_sms_template, litemall_sms_record               │                                                                              
├──────────────────────────────────┼──────────────────────────────────────────────────────────┤                                                                                
│ V14__add_goods_enhancements.sql  │ litemall_goods_rule, _description, _log                  │                                                                              
└──────────────────────────────────┴──────────────────────────────────────────────────────────┘

Undo scripts (db/undo/) — run manually to roll back

Each U{n}__undo_*.sql reverses exactly its matching V{n}. Run them in descending order (U14 → U2) to unwind cleanly.
                                                                                                                                                                               
---                                                                                                                                                                            
How rollback works

On your existing database (first start): Flyway sees baseline-on-migrate: true, marks V1 as done without running it, then runs V2–V14 forward.

To roll back a specific version, run its undo script directly:
# Example: undo only V14 (goods enhancements)
mysql -u litemall -p litemall < litemall-db/src/main/resources/db/undo/U14__undo_goods_enhancements.sql

# Then tell Flyway to forget V14 so it doesn't re-apply it on restart
# In MySQL:
DELETE FROM flyway_schema_history WHERE version = '14';

To roll back everything to baseline (V2–V14 in reverse order):                                                                                                               
for n in 14 13 12 11 10 9 8 7 6 5 4 3 2; do                                                                                                                                    
mysql -u litemall -p litemall < db/undo/U${n}__undo_*.sql                                                                                                                  
done                  